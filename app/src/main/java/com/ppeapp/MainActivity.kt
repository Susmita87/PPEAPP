package com.ppeapp

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ppeapp.ui.theme.PPEAndroidAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var detector: ONNXDetector
    private val tracker = Tracker()

    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        detector = ONNXDetector(this)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted -> 
            Log.d("CAMERA", "Permission granted: $isGranted")
        }
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {
            PPEAndroidAppTheme {
                val context = LocalContext.current
                var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
                var bitmap by remember { mutableStateOf<Bitmap?>(null) }
                var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
                var frameSize by remember { mutableStateOf<Size?>(null) }
                var liveCamera by remember { mutableStateOf(false) }
                var isProcessing by remember { mutableStateOf(false) }
                
                val coroutineScope = rememberCoroutineScope()

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        coroutineScope.launch {
                            isProcessing = true
                            try {
                                Log.d("UPLOAD", "Loading image from URI: $it")
                                val bmp = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(it)?.use { stream ->
                                        BitmapFactory.decodeStream(stream)
                                    }
                                }
                                bmp?.let { b ->
                                    bitmap = b
                                    Log.d("UPLOAD", "Image loaded: ${b.width}x${b.height}. Starting detection.")
                                    val results = withContext(Dispatchers.Default) { detector.detect(b) }
                                    detections = results
                                    Log.d("UPLOAD", "Detections received: ${results.size}")
                                }
                            } catch (e: Exception) {
                                Log.e("UPLOAD", "Error processing upload: ${e.message}")
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = {
                            liveCamera = false
                            bitmap = null
                            detections = emptyList()
                            launcher.launch("image/*")
                        }) { Text("Upload") }

                        Button(onClick = {
                            liveCamera = true
                            bitmap = null
                            detections = emptyList()
                        }) { Text("Live") }

                        Button(onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) 
                                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        }) { Text("Switch") }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .background(Color.Black)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!liveCamera) {
                            bitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(), 
                                    contentDescription = null, 
                                    modifier = Modifier.fillMaxSize()
                                )
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val scaleX = size.width / bmp.width
                                    val scaleY = size.height / bmp.height
                                    Log.d("UI", "Drawing upload boxes. Canvas size: ${size.width}x${size.height}. Detections: ${detections.size}")
                                    detections.forEach { det ->
                                        drawRect(
                                            color = if (det.className.contains("NO")) Color.Red else Color.Green,
                                            topLeft = Offset(det.x1 * scaleX, det.y1 * scaleY),
                                            size = Size((det.x2 - det.x1) * scaleX, (det.y2 - det.y1) * scaleY),
                                            style = Stroke(width = 8f)
                                        )
                                    }
                                }
                            }
                        } else {
                            val previewView = remember { PreviewView(context) }
                            LaunchedEffect(lensFacing) {
                                Log.d("CAMERA", "Starting camera with lensFacing=$lensFacing")
                                startCamera(previewView, lensFacing) { results, size ->
                                    detections = results
                                    frameSize = size
                                }
                            }
                            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                frameSize?.let { fSize ->
                                    val scaleX = size.width / fSize.width
                                    val scaleY = size.height / fSize.height
                                    detections.forEach { det ->
                                        drawRect(
                                            color = if (det.className.contains("NO")) Color.Red else Color.Green,
                                            topLeft = Offset(det.x1 * scaleX, det.y1 * scaleY),
                                            size = Size((det.x2 - det.x1) * scaleX, (det.y2 - det.y1) * scaleY),
                                            style = Stroke(width = 8f)
                                        )
                                    }
                                }
                            }
                        }

                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
        }
    }

    @ExperimentalGetImage
    private fun startCamera(previewView: PreviewView, lensFacing: Int, onDetection: (List<Detection>, Size) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        try {
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val bitmap = mediaImageToBitmap(mediaImage, rotation)
                            val results = detector.detect(bitmap)
                            val tracked = tracker.update(results)
                            onDetection(tracked, Size(bitmap.width.toFloat(), bitmap.height.toFloat()))
                        } catch (e: Exception) {
                            Log.e("CAMERA", "Error in analyzer: ${e.message}")
                        }
                    }
                    imageProxy.close()
                }

                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, imageAnalysis)
                Log.d("CAMERA", "Camera bound to lifecycle successfully")
            } catch (e: Exception) { 
                Log.e("CAMERA", "Error starting camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @ExperimentalGetImage
    fun mediaImageToBitmap(image: android.media.Image, rotationDegrees: Int): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }
}
