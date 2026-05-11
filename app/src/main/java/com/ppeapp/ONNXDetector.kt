package com.ppeapp

import android.content.Context
import ai.onnxruntime.*
import java.nio.FloatBuffer
import android.graphics.Bitmap
import androidx.core.graphics.scale

class ONNXDetector(context: Context) {

    private var env: OrtEnvironment
    private var session: OrtSession

    init {

        env = OrtEnvironment.getEnvironment()

        val modelBytes = context.assets.open("best.onnx").readBytes()

        session = env.createSession(
            modelBytes,
            OrtSession.SessionOptions()
        )

        println("ONNX model loaded successfully")
    }

    fun getInputShape(): LongArray {

        val inputInfo = session.inputInfo.values.first()

        return (inputInfo.info as TensorInfo).shape
    }

    fun bitmapToTensor(bitmap: Bitmap): OnnxTensor {

        val resized = bitmap.scale(640, 640)

        val floatValues = FloatArray(1 * 3 * 640 * 640)

        val pixels = IntArray(640 * 640)

        resized.getPixels(
            pixels,
            0,
            640,
            0,
            0,
            640,
            640
        )

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


        val output = outputs[0].value as Array<Array<FloatArray>>

        android.util.Log.d(
            "YOLO_SHAPE",
            "dim0=${output.size} dim1=${output[0].size} dim2=${output[0][0].size}"
        )

        android.util.Log.d(
            "YOLO_SAMPLE",
            """
                x=${output[0][0][0]}
                y=${output[0][1][0]}
                w=${output[0][2][0]}
                h=${output[0][3][0]}
                obj=${output[0][4][0]}
                class0=${output[0][5][0]}
                class1=${output[0][6][0]}
                """.trimIndent()
        )

        val detections = mutableListOf<Detection>()

        val rows = output[0][0].size

        val labels = listOf(
            "Hardhat",
            "Mask",
            "NO-Hardhat",
            "NO-Mask",
            "NO-Safety Vest",
            "Person",
            "Safety Cone",
            "Safety Vest",
            "machinery",
            "vehicle"
        )

        for (i in 0 until rows) {

            val x = output[0][0][i]
            val y = output[0][1][i]
            val w = output[0][2][i]
            val h = output[0][3][i]

            var maxClassScore = 0f
            var classId = -1

            val objectConfidence = output[0][4][i]



            for (c in 5 until output[0].size) {

                val classScore = output[0][c][i]

                if (classScore > maxClassScore) {
                    maxClassScore = classScore
                    classId = c - 5
                }
            }

            android.util.Log.d(
                "YOLO_RAW",
                "i=$i class=$classId score=$maxClassScore"
            )

            if (maxClassScore > 0.3f) {
                android.util.Log.d(
                    "YOLO_VALID",
                    "i=$i class=${labels[classId]} score=$maxClassScore"
                )
            }

            val finalConfidence = maxClassScore

            // confidence threshold
            if (finalConfidence > 0.3f && classId >= 0) {

                val imgW = bitmap.width.toFloat()
                val imgH = bitmap.height.toFloat()

                val x1 = (x - w / 2f) * imgW
                val y1 = (y - h / 2f) * imgH
                val x2 = (x + w / 2f) * imgW
                val y2 = (y + h / 2f) * imgH

                android.util.Log.d(
                    "YOLO",
                    "CLASS=${labels[classId]} CONF=$finalConfidence"
                )

                detections.add(
                    Detection(
                        classId = classId,
                        className = labels[classId],
                        confidence = finalConfidence,
                        x1 = x1,
                        y1 = y1,
                        x2 = x2,
                        y2 = y2
                    )
                )
            }
        }

        return detections
    }
}