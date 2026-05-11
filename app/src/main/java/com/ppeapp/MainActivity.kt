package com.ppeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface

class MainActivity : ComponentActivity() {

    private lateinit var detector: ONNXDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        detector = ONNXDetector(this)

        setContent {

            var bitmap by remember {
                mutableStateOf<Bitmap?>(null)
            }

            var detections by remember {
                mutableStateOf<List<Detection>>(emptyList())
            }

            val imagePicker =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->

                    if (uri != null) {

                        // =========================
                        // Decode bitmap
                        // =========================

                        val inputStream =
                            contentResolver.openInputStream(uri)

                        val originalBitmap =
                            BitmapFactory.decodeStream(inputStream)

                        // =========================
                        // Read EXIF rotation
                        // =========================

                        val exifStream =
                            contentResolver.openInputStream(uri)

                        val exif =
                            ExifInterface(exifStream!!)

                        val rotation = when (
                            exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                        ) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }

                        val matrix = Matrix()
                        matrix.postRotate(rotation)

                        val rotatedBitmap =
                            Bitmap.createBitmap(
                                originalBitmap,
                                0,
                                0,
                                originalBitmap.width,
                                originalBitmap.height,
                                matrix,
                                true
                            )

                        // =========================
                        // Run detection
                        // =========================

                        bitmap = rotatedBitmap

                        detections =
                            detector.detect(rotatedBitmap)

                        println("DETECTIONS = $detections")
                    }
                }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {

                Button(
                    onClick = {
                        imagePicker.launch("image/*")
                    }
                ) {
                    Text("Select Image")
                }

                Spacer(modifier = Modifier.height(16.dp))

                bitmap?.let { bmp ->

                    val imageWidth = bmp.width.toFloat()
                    val imageHeight = bmp.height.toFloat()

                    val aspectRatio = imageWidth / imageHeight

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                    ) {

                        // =========================
                        // IMAGE
                        // =========================

                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.matchParentSize()
                        )

                        // =========================
                        // BOUNDING BOXES
                        // =========================

                        Canvas(
                            modifier = Modifier.matchParentSize()
                        ) {

                            val scaleX = size.width / imageWidth
                            val scaleY = size.height / imageHeight

                            detections.forEach { det ->

                                val left = det.x1 * scaleX
                                val top = det.y1 * scaleY
                                val width = (det.x2 - det.x1) * scaleX
                                val height = (det.y2 - det.y1) * scaleY

                                drawRect(
                                    color = Color.Red,
                                    topLeft = Offset(left, top),
                                    size = Size(width, height),
                                    style = Stroke(width = 4f)
                                )

                                drawContext.canvas.nativeCanvas.drawText(
                                    "${det.className} ${(det.confidence * 100).toInt()}%",
                                    left,
                                    top - 10,
                                    android.graphics.Paint().apply {
                                        color = android.graphics.Color.RED
                                        textSize = 40f
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    detections.forEach {
                        Text(
                            text = "${it.className} : ${"%.2f".format(it.confidence)}"
                        )
                    }
                }
            }
        }
    }
}