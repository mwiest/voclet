package com.github.mwiest.voclet.ui.wordlist

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Four draggable corners over a photo shown with `ContentScale.Fit` in the same
 * box, marking the part of the page to scan.
 */
@Composable
fun PageSelector(
    imageWidth: Int,
    imageHeight: Int,
    selection: PageQuad,
    onSelectionChange: (PageQuad) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSelection by rememberUpdatedState(selection)
    val currentOnChange by rememberUpdatedState(onSelectionChange)
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    val accent = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.onPrimary

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fit = with(density) {
            FitRect.of(imageWidth, imageHeight, maxWidth.toPx(), maxHeight.toPx())
        }
        val touchRadius = with(density) { TOUCH_RADIUS.toPx() }
        val handleRadius = with(density) { HANDLE_RADIUS.toPx() }
        val outlineWidth = with(density) { 2.dp.toPx() }
        val ringWidth = with(density) { 3.dp.toPx() }
        val grab = remember { arrayOfNulls<PageCorner>(1) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(fit, touchRadius) {
                    detectDragGestures(
                        onDragStart = { start ->
                            grab[0] = fit.cornerNear(currentSelection, start.x, start.y, touchRadius)
                        },
                        onDragEnd = { grab[0] = null },
                        onDragCancel = { grab[0] = null },
                    ) { change, drag ->
                        val corner = grab[0] ?: return@detectDragGestures
                        change.consume()
                        // Follows the drag rather than the finger, so grabbing a
                        // handle off-centre does not make it jump.
                        val (x, y) = fit.toBox(currentSelection[corner])
                        currentSelection.moved(corner, fit.toImage(x + drag.x, y + drag.y))
                            ?.let(currentOnChange)
                    }
                }
        ) {
            val points = selection.corners.map { fit.toBox(it).let { (x, y) -> Offset(x, y) } }
            val quad = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val outside = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(fit.left, fit.top, fit.left + fit.width, fit.top + fit.height))
                addPath(quad)
            }
            drawPath(outside, scrim)
            drawPath(quad, accent, style = Stroke(width = outlineWidth))
            points.forEach { center ->
                drawCircle(accent, handleRadius, center)
                drawCircle(ring, handleRadius, center, style = Stroke(width = ringWidth))
            }
        }
    }
}

/** The selected quad flattened into an upright rectangle, as `PageReader` crops its lines. */
fun Bitmap.warpedTo(quad: PageQuad): Bitmap {
    val size = quad.outputSize(width, height)
    val source = FloatArray(8)
    quad.corners.forEachIndexed { index, corner ->
        source[2 * index] = corner.x * width
        source[2 * index + 1] = corner.y * height
    }
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val destination = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)

    val page = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    Canvas(page).drawBitmap(
        this,
        Matrix().apply { setPolyToPoly(source, 0, destination, 0, 4) },
        Paint(Paint.FILTER_BITMAP_FLAG),
    )
    return page
}

private val TOUCH_RADIUS = 48.dp
private val HANDLE_RADIUS = 12.dp
