package com.ppeapp

import android.content.Context
import ai.onnxruntime.*
import java.nio.FloatBuffer
import android.graphics.Bitmap
import androidx.core.graphics.scale
import android.util.Log

class ONNXDetector(context: Context) {

    private var env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession

    init {
        try {
            val modelBytes = context.assets.open("best.onnx").readBytes()
            session = env.createSession(
                modelBytes,
                OrtSession.SessionOptions()
            )
            Log.d("DETECTOR", "ONNX model loaded successfully. Session names: ${session.outputNames}")
        } catch (e: Exception) {
            Log.e("DETECTOR", "Error loading model: ${e.message}")
            throw e
        }
    }

    fun bitmapToTensor(bitmap: Bitmap): OnnxTensor {
        val resized = bitmap.scale(640, 640)
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

    fun detect(bitmap: Bitmap): List<Detection> {
        val inputTensor = bitmapToTensor(bitmap)
        val outputs = session.run(
            mapOf(
                session.inputNames.iterator().next() to inputTensor
            )
        )

        @Suppress("UNCHECKED_CAST")
        val output = outputs[0].value as Array<Array<FloatArray>>
        val detections = mutableListOf<Detection>()
        val rows = output[0][0].size
        val classesCount = output[0].size - 4
        
        Log.d("DETECTOR", "Output shape: [1][${output[0].size}][$rows]. Classes count detected: $classesCount")

        val labels = listOf(
            "Hardhat", "Mask", "NO-Hardhat", "NO-Mask", "NO-Safety Vest",
            "Person", "Safety Cone", "Safety Vest", "machinery", "vehicle"
        )

        for (i in 0 until rows) {
            var maxClassScore = 0f
            var classId = -1

            // Check if model has objectness at index 4
            // Some models have [x,y,w,h,obj,c1,c2...] others have [x,y,w,h,c1,c2...]
            // Let's try both paths if we find no detections.
            
            // Assuming stable code index (classes start at 5)
            for (c in 5 until output[0].size) {
                val classScore = output[0][c][i]
                if (classScore > maxClassScore) {
                    maxClassScore = classScore
                    classId = c - 5
                }
            }

            if (maxClassScore > 0.3f && classId >= 0 && classId < labels.size) {
                val x = output[0][0][i]
                val y = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]
                
                val imgW = bitmap.width.toFloat()
                val imgH = bitmap.height.toFloat()

                val x1 = (x - w / 2f) * imgW
                val y1 = (y - h / 2f) * imgH
                val x2 = (x + w / 2f) * imgW
                val y2 = (y + h / 2f) * imgH

                detections.add(
                    Detection(
                        classId = classId,
                        className = labels[classId],
                        confidence = maxClassScore,
                        x1 = x1,
                        y1 = y1,
                        x2 = x2,
                        y2 = y2
                    )
                )
            }
        }
        
        // Fallback for YOLOv8 (classes start at 4)
        if (detections.isEmpty()) {
            for (i in 0 until rows) {
                var maxClassScore = 0f
                var classId = -1
                for (c in 4 until output[0].size) {
                    val classScore = output[0][c][i]
                    if (classScore > maxClassScore) {
                        maxClassScore = classScore
                        classId = c - 4
                    }
                }
                if (maxClassScore > 0.3f && classId >= 0 && classId < labels.size) {
                    val x = output[0][0][i]
                    val y = output[0][1][i]
                    val w = output[0][2][i]
                    val h = output[0][3][i]
                    val x1 = (x - w / 2f) * bitmap.width
                    val y1 = (y - h / 2f) * bitmap.height
                    val x2 = (x + w / 2f) * bitmap.width
                    val y2 = (y + h / 2f) * bitmap.height
                    detections.add(Detection(classId, labels[classId], maxClassScore, x1, y1, x2, y2))
                }
            }
        }

        Log.d("DETECTOR", "Found ${detections.size} detections")
        return detections
    }
}
