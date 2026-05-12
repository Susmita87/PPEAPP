package com.ppeapp

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.zIndex
import com.ppeapp.ui.theme.PPEAndroidAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Main Activity of the PPE Detection application.
 * Handles the UI, camera lifecycle, and user interactions for image upload and live detection.
 */
class MainActivity : ComponentActivity() {

    private lateinit var detector: ONNXDetector
    private val tracker = Tracker()
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    @ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        detector = ONNXDetector(this)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

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
                var isJsonExpanded by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                val previewView = remember {
                    PreviewView(context).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                    }
                }

                // Ensure the camera analyzer is stopped and pending results are ignored when switching modes
                val isLiveMode = rememberUpdatedState(liveCamera)

                LaunchedEffect(liveCamera, lensFacing) {
                    if (liveCamera) {
                        startCamera(previewView, detector, lensFacing) { result, size ->
                            if (isLiveMode.value) {
                                android.util.Log.d("PPE_DEBUG", "Detections: ${result.size}, Size: $size")
                                detections = result
                                frameSize = size
                            }
                        }
                    } else {
                        cameraProvider?.unbindAll()
                        frameSize = null
                    }
                }

                // Launcher for selecting images from device storage
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        coroutineScope.launch {
                            isProcessing = true
                            try {
                                val bmp = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(it)?.use { stream ->
                                        BitmapFactory.decodeStream(stream)
                                    }
                                }

                                bmp?.let { b ->
                                    // Handle EXIF rotation for uploaded images
                                    val inputStream = context.contentResolver.openInputStream(it)
                                    val exif = inputStream?.let { stream ->
                                        try {
                                            androidx.exifinterface.media.ExifInterface(stream)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    val orientation = exif?.getAttributeInt(
                                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                                    )
                                    
                                    val rotatedBmp = when (orientation) {
                                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(b, 90f)
                                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(b, 180f)
                                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(b, 270f)
                                        else -> b
                                    }

                                    bitmap = rotatedBmp
                                    detections = emptyList()
                                    val results = withContext(Dispatchers.Default) {
                                        detector.detect(rotatedBmp)
                                    }
                                    detections = results
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(16.dp)
                ) {
                    // Control row with buttons for Upload, Live Camera, and Switching Lens
                    Row(
                        modifier = Modifier.fillMaxWidth().zIndex(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                detections = emptyList()
                                liveCamera = false
                                launcher.launch("image/*")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Upload")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                detections = emptyList()
                                liveCamera = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Live")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                detections = emptyList()
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Switch")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Viewport for displaying either the static bitmap or the live camera feed
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .background(Color.Black)
                            .clipToBounds()
                            .zIndex(0f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!liveCamera) {
                            bitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.matchParentSize()
                                )

                                if (!isProcessing) {
                                    Canvas(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .zIndex(1f)
                                    ) {
                                        val canvasRatio = size.width / size.height
                                        val bmpRatio = bmp.width.toFloat() / bmp.height.toFloat()
                                        
                                        val scale = if (bmpRatio > canvasRatio) {
                                            size.width / bmp.width
                                        } else {
                                            size.height / bmp.height
                                        }
                                        
                                        val offsetX = (size.width - bmp.width * scale) / 2
                                        val offsetY = (size.height - bmp.height * scale) / 2

                                        detections.forEach { det ->
                                            val left = det.x1 * scale + offsetX
                                            val top = det.y1 * scale + offsetY
                                            val right = det.x2 * scale + offsetX
                                            val bottom = det.y2 * scale + offsetY

                                            drawRect(
                                                color = if (det.className.contains("NO")) Color.Red else Color.Green,
                                                topLeft = Offset(left, top),
                                                size = Size(right - left, bottom - top),
                                                style = Stroke(width = 5f)
                                            )

                                            drawContext.canvas.nativeCanvas.drawText(
                                                "${det.className} ${(det.confidence * 100).toInt()}%",
                                                left,
                                                top - 10f,
                                                android.graphics.Paint().apply {
                                                    color = android.graphics.Color.YELLOW
                                                    textSize = 40f
                                                    isFakeBoldText = true
                                                    setShadowLayer(5f, 0f, 0f, android.graphics.Color.BLACK)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.matchParentSize()
                            )

                            Canvas(
                                modifier = Modifier
                                    .matchParentSize()
                                    .zIndex(1f)
                            ) {
                                frameSize?.let { fSize ->
                                    // Calculate scaling to match FIT_CENTER
                                    val canvasRatio = size.width / size.height
                                    val frameRatio = fSize.width / fSize.height
                                    
                                    val scale = if (frameRatio > canvasRatio) {
                                        size.width / fSize.width
                                    } else {
                                        size.height / fSize.height
                                    }
                                    
                                    val offsetX = (size.width - fSize.width * scale) / 2
                                    val offsetY = (size.height - fSize.height * scale) / 2

                                    detections.forEach { det ->
                                        val left = det.x1 * scale + offsetX
                                        val top = det.y1 * scale + offsetY
                                        val right = det.x2 * scale + offsetX
                                        val bottom = det.y2 * scale + offsetY

                                        drawRect(
                                            color = if (det.className.contains("NO")) Color.Red else Color.Green,
                                            topLeft = Offset(left, top),
                                            size = Size(right - left, bottom - top),
                                            style = Stroke(width = 4f)
                                        )

                                        drawContext.canvas.nativeCanvas.drawText(
                                            "${det.className} ${(det.confidence * 100).toInt()}%",
                                            left,
                                            top - 10f,
                                            android.graphics.Paint().apply {
                                                color = android.graphics.Color.YELLOW
                                                textSize = 40f
                                                isFakeBoldText = true
                                                setShadowLayer(5f, 0f, 0f, android.graphics.Color.BLACK)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (isProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Processing...", color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Expandable section for displaying raw detection results in JSON format
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isJsonExpanded = !isJsonExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isJsonExpanded) "▼ Detection Results (JSON)" else "▶ Detection Results (JSON)",
                            color = Color.Gray,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isJsonExpanded) "Minimize" else "Maximize",
                            color = Color(0xFF9CDCFE)
                        )
                    }

                    if (isJsonExpanded) {
                        val jsonText = if (detections.isEmpty()) "[]" else {
                            detections.joinToString(
                                prefix = "[\n",
                                postfix = "\n]",
                                separator = ",\n"
                            ) { det ->
                                """  {
    "label": "${det.className}",
    "confidence": ${"%.2f".format(det.confidence)},
    "trackId": ${det.trackId},
    "bbox": [${det.x1.toInt()}, ${det.y1.toInt()}, ${det.x2.toInt()}, ${det.y2.toInt()}]
  }"""
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF1E1E1E))
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        ) {
                            Text(
                                text = jsonText,
                                color = Color(0xFF9CDCFE),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Configures and starts the CameraX camera feed with image analysis for PPE detection.
     */
    @ExperimentalGetImage
    private fun startCamera(
        previewView: PreviewView,
        detector: ONNXDetector,
        lensFacing: Int,
        onDetection: (List<Detection>, Size) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            this.cameraProvider = provider

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            var lastInferenceTime = 0L

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastInferenceTime < 200) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                lastInferenceTime = currentTime
                
                try {
                    // Get bitmap from ImageProxy
                    var bitmap = imageProxy.toBitmap()
                    
                    // Rotate bitmap to match display orientation if needed
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    if (rotation != 0) {
                        val matrix = android.graphics.Matrix()
                        matrix.postRotate(rotation.toFloat())
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }
                    
                    val results = detector.detect(bitmap)
                    val tracked = tracker.update(results)
                    
                    val currentSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
                    
                    ContextCompat.getMainExecutor(this@MainActivity).execute {
                        onDetection(tracked, currentSize)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CAMERA_DETECT", "Error in analyzer: ${e.message}")
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Converts a media.Image from the camera feed into a Bitmap, handling format conversion and rotation.
     */
    @ExperimentalGetImage
    fun mediaImageToBitmap(
        image: android.media.Image,
        rotationDegrees: Int
    ): Bitmap {
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

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)

        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Optimization: Skip matrix operations if no rotation is needed
        if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        return bitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        detector.onDestroy()
    }
}
