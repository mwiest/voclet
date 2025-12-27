package com.github.mwiest.voclet.ui.wordlist

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.ui.theme.VocletTheme
import java.util.concurrent.Executors
import androidx.compose.ui.tooling.preview.Preview as PreviewAnnotation

@Composable
fun CameraDialog(
    onDismiss: () -> Unit,
    onImageCaptured: (Bitmap) -> Unit,
    isProcessing: Boolean,
    errorMessage: String?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }

    // Initialize camera
    DisposableEffect(Unit) {
        val cameraExecutor = Executors.newSingleThreadExecutor()

        val previewBuilder = Preview.Builder().build()
        preview = previewBuilder

        val imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = imageCaptureBuilder

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewBuilder,
                    imageCaptureBuilder
                )
            } catch (e: Exception) {
                android.util.Log.e("CameraDialog", "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    // Connect preview to surface provider when previewView is created
    LaunchedEffect(previewView, preview) {
        if (previewView != null && preview != null) {
            preview?.setSurfaceProvider(previewView?.surfaceProvider)
        }
    }

    Dialog(
        onDismissRequest = { if (!isCapturing) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isCapturing,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        CameraDialogContent(
            onDismiss = onDismiss,
            isProcessing = isProcessing,
            isCapturing = isCapturing,
            capturedBitmap = capturedBitmap,
            errorMessage = errorMessage,
            onPreviewViewCreated = { previewView = it },
            onCaptureClick = {
                isCapturing = true
                imageCapture?.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = imageProxyToBitmap(image)
                            image.close()
                            isCapturing = false
                            capturedBitmap = bitmap
                            onImageCaptured(bitmap)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            android.util.Log.e(
                                "CameraDialog",
                                "Image capture failed",
                                exception
                            )
                            isCapturing = false
                        }
                    }
                )
            }
        )
    }
}

@Composable
private fun CameraDialogContent(
    onDismiss: () -> Unit,
    isProcessing: Boolean,
    isCapturing: Boolean,
    capturedBitmap: Bitmap?,
    errorMessage: String?,
    onPreviewViewCreated: (PreviewView) -> Unit,
    onCaptureClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Camera preview or captured image
            if (capturedBitmap != null) {
                // Show captured image
                Image(
                    bitmap = capturedBitmap.asImageBitmap(),
                    contentDescription = "Captured image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Show live camera preview
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { onPreviewViewCreated(it) }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Close button with circular semi-transparent background
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ) {
                IconButton(
                    onClick = onDismiss,
                    enabled = !isCapturing
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(id = R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Error message
            if (errorMessage != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp, start = 16.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Capture button or processing indicator
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            ) {
                if (isProcessing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(72.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .5f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                text = stringResource(id = R.string.extracting_word_pairs),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    FloatingActionButton(
                        onClick = onCaptureClick,
                        modifier = Modifier
                            .size(72.dp)
                            .border(4.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Camera,
                            contentDescription = stringResource(id = R.string.camera_capture),
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Convert ImageProxy to Bitmap with proper rotation.
 */
private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    // Rotate bitmap based on image rotation
    val rotationDegrees = image.imageInfo.rotationDegrees
    if (rotationDegrees != 0) {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    return bitmap
}

@PreviewAnnotation(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
fun CameraCapturePreview() {
    VocletTheme {
        CameraDialogContent(
            onDismiss = {},
            isProcessing = false,
            isCapturing = false,
            capturedBitmap = null,
            errorMessage = null,
            onPreviewViewCreated = {},
            onCaptureClick = {}
        )
    }
}

@PreviewAnnotation(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun CameraCaptureDarkTabletPreview() {
    VocletTheme(darkTheme = true) {
        CameraDialogContent(
            onDismiss = {},
            isProcessing = true,
            isCapturing = false,
            capturedBitmap = null,
            errorMessage = "Example error message",
            onPreviewViewCreated = {},
            onCaptureClick = {}
        )
    }
}
