package com.github.mwiest.voclet.data.ai.cloud

import kotlin.math.max
import kotlin.math.roundToInt

/** Pixel dimensions, kept Android-free so the arithmetic stays unit-testable. */
data class ImageSize(val width: Int, val height: Int)

/**
 * How far a captured photo should be shrunk before it goes on the wire.
 *
 * A tablet camera produces several megapixels; JPEG-compressed and then
 * Base64-encoded (which adds a third again) that is megabytes uploaded over
 * mobile data before the model even starts reading, and some providers reject
 * images past a size limit outright. Vocabulary-list text stays comfortably
 * legible far below full resolution, so the long edge is capped and the short
 * edge follows to preserve the aspect ratio.
 */
object ImageScaling {

    /**
     * Target size for an image of [width] x [height], or null when it already
     * fits within [maxLongEdge] and should be sent untouched.
     *
     * Neither side is ever rounded down to zero, so an extreme panorama still
     * produces a valid bitmap.
     */
    fun targetSize(width: Int, height: Int, maxLongEdge: Int): ImageSize? {
        if (width <= 0 || height <= 0) return null
        val longEdge = max(width, height)
        if (longEdge <= maxLongEdge) return null

        val factor = maxLongEdge.toDouble() / longEdge
        return ImageSize(
            width = (width * factor).roundToInt().coerceAtLeast(1),
            height = (height * factor).roundToInt().coerceAtLeast(1),
        )
    }
}
