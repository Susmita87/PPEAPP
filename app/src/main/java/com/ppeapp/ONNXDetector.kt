package com.ppeapp

import android.content.Context
import ai.onnxruntime.*
import java.nio.FloatBuffer
import android.graphics.Bitmap
import androidx.core.graphics.scale

/**
 * Detector class that leverages the ONNX Runtime to perform PPE detection using a YOLO model.
 * Intelligent association: Binds PPE detections to the specific person they belong to.
 */
class ONNXDetector(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private var nextTrackId = 1
    private val trackedPersons = mutableListOf<Detection>()

    init {
        val modelBytes = context.assets.open("best-stage2-v8.onnx").readBytes()
        val options = OrtSession.SessionOptions()
        
        try {
            options.addNnapi()
        } catch (_: Exception) {
            android.util.Log.w("ONNX", "NNAPI not supported, falling back to CPU")
        }
        
        session = env.createSession(modelBytes, options)
    }

    /**
     * Converts a Bitmap to an OnnxTensor suitable for YOLO input.
     */
    fun bitmapToTensor(bitmap: Bitmap): OnnxTensor {
        val resized = if (bitmap.width == 640 && bitmap.height == 640) bitmap else bitmap.scale(640, 640)
        val floatValues = FloatArray(1 * 3 * 640 * 640)
        val pixels = IntArray(640 * 640)

        resized.getPixels(pixels, 0, 640, 0, 0, 640, 640)

        var rIndex = 0
        var gIndex = 640 * 640
        var bIndex = 2 * 640 * 640

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            floatValues[rIndex++] = r
            floatValues[gIndex++] = g
            floatValues[bIndex++] = b
        }

        return OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(floatValues),
            longArrayOf(1, 3, 640, 640)
        )
    }

    /**
     * Executes the intelligent detection pipeline.
     * Logic: Detect persons -> Track them -> Crop for detail -> Associate PPE with Person ID.
     */
    fun detect(bitmap: Bitmap): List<Detection> {
        // 1. Initial full-frame inference
        val allDetections = runInference(bitmap)
        android.util.Log.d("PPE_DETECTOR", "Raw detections: ${allDetections.size}")
        
        // 2. Identify and track persons
        val currentPersons = allDetections.filter { it.className == "Person" }
            .sortedByDescending { (it.x2 - it.x1) * (it.y2 - it.y1) }
            .take(5)

        for (person in currentPersons) {
            var assignedId = -1
            for (tracked in trackedPersons) {
                if (iou(person, tracked) > 0.5f) {
                    assignedId = tracked.trackId
                    break
                }
            }
            if (assignedId == -1) assignedId = nextTrackId++
            person.trackId = assignedId
        }
        
        trackedPersons.clear()
        trackedPersons.addAll(currentPersons)

        val results = mutableListOf<Detection>()
        results.addAll(allDetections) // Add everything by default first

        for (person in currentPersons) {
            // results.add(person) // Already added via allDetections

            // 3. Intelligent Association: Crop the person with padding to detect PPE status
            try {
                val padW = (person.x2 - person.x1) * 0.1f
                val padH = (person.y2 - person.y1) * 0.1f
                
                val left = (person.x1 - padW).coerceAtLeast(0f)
                val top = (person.y1 - padH).coerceAtLeast(0f)
                val right = (person.x2 + padW).coerceAtMost(bitmap.width.toFloat())
                val bottom = (person.y2 + padH).coerceAtMost(bitmap.height.toFloat())

                if ((right - left) < 20 || (bottom - top) < 20) continue

                val crop = Bitmap.createBitmap(
                    bitmap, 
                    left.toInt(), 
                    top.toInt(), 
                    (right - left).toInt(), 
                    (bottom - top).toInt()
                )
                
                val ppeResults = runInference(crop)
                android.util.Log.d("PPE_DETECTOR", "PPE results for person ${person.trackId}: ${ppeResults.size}")

                // 4. Map PPE detections back to global coordinates and bind to Person ID
                for (ppe in ppeResults) {
                    if (ppe.className.contains("Hardhat") || 
                        ppe.className.contains("Vest") || 
                        ppe.className.contains("Mask")) {
                        
                        results.add(ppe.copy(
                            trackId = person.trackId,
                            x1 = ppe.x1 + left,
                            y1 = ppe.y1 + top,
                            x2 = ppe.x2 + left,
                            y2 = ppe.y2 + top
                        ))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PPE_DETECTOR", "Error in association: ${e.message}")
            }
        }

        return results
    }

    /**
     * Performs a single inference pass.
     */
    private fun runInference(bitmap: Bitmap): List<Detection> {
        val inputTensor = bitmapToTensor(bitmap)
        val outputs = session.run(mapOf(session.inputNames.iterator().next() to inputTensor))
        
        @Suppress("UNCHECKED_CAST")
        val output = outputs[0].value as Array<Array<FloatArray>>
        
        val detections = mutableListOf<Detection>()
        val rows = output[0][0].size
        val labels = listOf("Hardhat", "Mask", "NO-Hardhat", "NO-Mask", "NO-Safety Vest", "Person", "Safety Cone", "Safety Vest", "machinery", "vehicle")

        for (i in 0 until rows) {
            var maxClassScore = 0f
            var classId = -1
            
            // Standard YOLOv8 output: coordinates in [0, 1, 2, 3], classes starting from index 4
            for (c in 4 until output[0].size) {
                val score = output[0][c][i]
                if (score > maxClassScore) {
                    maxClassScore = score
                    classId = c - 4
                }
            }

            if (maxClassScore > 0.4f && classId >= 0 && classId < labels.size) {
                // YOLOv8 output coordinates are typically in pixels relative to the model input (640x640)
                // We need to normalize them (divide by 640) before scaling to the original bitmap size
                val x = output[0][0][i] / 640f
                val y = output[0][1][i] / 640f
                val w = output[0][2][i] / 640f
                val h = output[0][3][i] / 640f

                detections.add(Detection(
                    classId = classId,
                    className = labels[classId],
                    confidence = maxClassScore,
                    x1 = (x - w / 2f) * bitmap.width,
                    y1 = (y - h / 2f) * bitmap.height,
                    x2 = (x + w / 2f) * bitmap.width,
                    y2 = (y + h / 2f) * bitmap.height
                ))
            }
        }
        
        // Very basic NMS to avoid clutter
        return nms(detections)
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()
        val active = BooleanArray(sorted.size) { true }
        
        for (i in sorted.indices) {
            if (active[i]) {
                selected.add(sorted[i])
                for (j in i + 1 until sorted.size) {
                    if (active[j] && iou(sorted[i], sorted[j]) > 0.45f) {
                        active[j] = false
                    }
                }
            }
        }
        return selected
    }

    private fun iou(a: Detection, b: Detection): Float {
        val x1 = maxOf(a.x1, b.x1)
        val y1 = maxOf(a.y1, b.y1)
        val x2 = minOf(a.x2, b.x2)
        val y2 = minOf(a.y2, b.y2)
        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        return intersection / (areaA + areaB - intersection + 1e-6f)
    }

    fun onDestroy() {
        session.close()
        env.close()
    }
}
