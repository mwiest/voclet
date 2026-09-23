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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.github.mwiest.voclet.data.ai.ocr.ReadProgress
import com.github.mwiest.voclet.ui.theme.VocletTheme
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.tooling.preview.Preview as PreviewAnnotation

@Composable
fun CameraDialog(
    onDismiss: () -> Unit,
    onImageCaptured: (Bitmap) -> Unit,
    isProcessing: Boolean,
    progress: ReadProgress?,
    errorMessage: String?,
    onErrorCleared: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scannedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selection by remember { mutableStateOf(PageQuad.inset()) }
    val scope = rememberCoroutineScope()
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
            progress = progress,
            isCapturing = isCapturing,
            capturedBitmap = capturedBitmap,
            scannedBitmap = scannedBitmap,
            selection = selection,
            onSelectionChange = { selection = it },
            errorMessage = errorMessage,
            onPreviewViewCreated = { previewView = it },
            // Retry re-runs the AI on the crop we already have: on-device
            // inference is slow enough that making the user shoot again for a
            // transient failure would be a poor trade.
            onRetry = { scannedBitmap?.let(onImageCaptured) },
            onRetake = {
                capturedBitmap = null
                scannedBitmap = null
                onErrorCleared()
            },
            onScanClick = {
                val photo = capturedBitmap ?: return@CameraDialogContent
                val quad = selection
                isCapturing = true
                scope.launch {
                    val page = withContext(Dispatchers.Default) { photo.warpedTo(quad) }
                    isCapturing = false
                    scannedBitmap = page
                    onImageCaptured(page)
                }
            },
            onCaptureClick = {
                isCapturing = true
                imageCapture?.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = imageProxyToBitmap(image)
                            image.close()
                            isCapturing = false
                            selection = PageQuad.inset()
                            capturedBitmap = bitmap
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
    progress: ReadProgress? = null,
    isCapturing: Boolean,
    capturedBitmap: Bitmap?,
    errorMessage: String?,
    onPreviewViewCreated: (PreviewView) -> Unit,
    onCaptureClick: () -> Unit,
    scannedBitmap: Bitmap? = null,
    selection: PageQuad = PageQuad.inset(),
    onSelectionChange: (PageQuad) -> Unit = {},
    onScanClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRetake: () -> Unit = {}
) {
    val isSelecting = capturedBitmap != null && scannedBitmap == null
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val shown = scannedBitmap ?: capturedBitmap
            if (shown != null) {
                // Fit, not Crop: the selector's corners have to reach the whole photo.
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                if (isSelecting) {
                    PageSelector(
                        imageWidth = shown.width,
                        imageHeight = shown.height,
                        selection = selection,
                        onSelectionChange = onSelectionChange,
                    )
                }
            } else {
                // Show live camera preview
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { onPreviewViewCreated(it) }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top bar with close button
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
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
            }

            if (isSelecting) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp, start = 16.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = stringResource(id = R.string.scan_select_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
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
                    ScanProgress(progress)
                } else if (errorMessage != null && scannedBitmap != null) {
                    // A failed scan leaves the photo on screen, so the way out
                    // has to be explicit: run it again, shoot a better one, or
                    // give up.
                    ScanErrorActions(
                        onRetry = onRetry,
                        onRetake = onRetake,
                        onCancel = onDismiss
                    )
                } else if (isSelecting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .85f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            TextButton(onClick = onRetake, enabled = !isCapturing) {
                                Text(text = stringResource(id = R.string.ai_scan_retake))
                            }
                        }
                        ShutterButton(
                            icon = Icons.Default.DocumentScanner,
                            contentDescription = stringResource(id = R.string.scan_start),
                            onClick = { if (!isCapturing) onScanClick() }
                        )
                    }
                } else {
                    ShutterButton(
                        icon = Icons.Default.Camera,
                        contentDescription = stringResource(id = R.string.camera_capture),
                        onClick = onCaptureClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(72.dp)
            .border(4.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * The wait while the page is read.
 *
 * The on-device reader counts the lines it has left, so the ring fills for
 * real; the cloud reports nothing at all and keeps the indeterminate one. The
 * two are the same 72 dp ring, so a local scan simply stops spinning and starts
 * filling once the detector has said how much work there is.
 */
@Composable
private fun ScanProgress(progress: ReadProgress?) {
    val lines = progress as? ReadProgress.Recognizing
    val fraction by animateFloatAsState(
        targetValue = lines?.let { it.linesRead.toFloat() / it.lines.coerceAtLeast(1) } ?: 0f,
        label = "scanProgress",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (lines == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
        } else {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = .5f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = lines
                    ?.let { stringResource(R.string.scan_lines_read, it.linesRead, it.lines) }
                    ?: stringResource(R.string.extracting_word_pairs),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * The three ways out of a failed scan. Retry is the primary action because most
 * on-device failures are timeouts, which a second run often survives.
 */
@Composable
private fun ScanErrorActions(
    onRetry: () -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = .85f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onRetry) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.ai_scan_retry))
            }
            OutlinedButton(onClick = onRetake) {
                Text(text = stringResource(id = R.string.ai_scan_retake))
            }
            TextButton(onClick = onCancel) {
                Text(text = stringResource(id = R.string.cancel))
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
            progress = ReadProgress.Recognizing(linesRead = 96, lines = 152),
            isCapturing = false,
            capturedBitmap = null,
            errorMessage = "Example error message",
            onPreviewViewCreated = {},
            onCaptureClick = {}
        )
    }
}

@PreviewAnnotation(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
fun CameraCaptureScanFailedPreview() {
    VocletTheme {
        // A 1x1 stand-in for the photo the failed scan keeps on screen, which is
        // what puts the dialog into its error state.
        val placeholder = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        CameraDialogContent(
            onDismiss = {},
            isProcessing = false,
            isCapturing = false,
            capturedBitmap = placeholder,
            scannedBitmap = placeholder,
            errorMessage = "The on-device AI took too long to answer.",
            onPreviewViewCreated = {},
            onCaptureClick = {}
        )
    }
}

@PreviewAnnotation(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
fun CameraCaptureSelectingPreview() {
    VocletTheme {
        val placeholder = Bitmap.createBitmap(3, 4, Bitmap.Config.ARGB_8888)
        CameraDialogContent(
            onDismiss = {},
            isProcessing = false,
            isCapturing = false,
            capturedBitmap = placeholder,
            errorMessage = null,
            onPreviewViewCreated = {},
            onCaptureClick = {},
            selection = PageQuad.inset().moved(PageCorner.TOP_LEFT, PagePoint(0.2f, 0.15f))!!
        )
    }
}
