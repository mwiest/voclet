package com.github.mwiest.voclet.ui.wordlist

import com.github.mwiest.voclet.data.ai.ocr.ImageSize
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** A point in normalized image coordinates: 0..1 on both axes, y pointing down. */
data class PagePoint(val x: Float, val y: Float)

enum class PageCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

/** The part of a photo to scan, corners clockwise from the top left. */
data class PageQuad(
    val topLeft: PagePoint,
    val topRight: PagePoint,
    val bottomRight: PagePoint,
    val bottomLeft: PagePoint,
) {
    val corners: List<PagePoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    operator fun get(corner: PageCorner): PagePoint = corners[corner.ordinal]

    /**
     * This quad with [corner] dragged to [to], clamped to the image, or null when
     * the move would fold the quad over itself or shrink it to a sliver.
     */
    fun moved(corner: PageCorner, to: PagePoint): PageQuad? {
        val clamped = PagePoint(to.x.coerceIn(0f, 1f), to.y.coerceIn(0f, 1f))
        val next = when (corner) {
            PageCorner.TOP_LEFT -> copy(topLeft = clamped)
            PageCorner.TOP_RIGHT -> copy(topRight = clamped)
            PageCorner.BOTTOM_RIGHT -> copy(bottomRight = clamped)
            PageCorner.BOTTOM_LEFT -> copy(bottomLeft = clamped)
        }
        return next.takeIf { it.isConvex() && it.shortestSide() >= MIN_SIDE }
    }

    /**
     * The size of the flattened crop in source pixels: each side as long as the
     * longer of the two edges it came from, so no text is squeezed.
     */
    fun outputSize(imageWidth: Int, imageHeight: Int): ImageSize {
        fun length(a: PagePoint, b: PagePoint) =
            hypot((b.x - a.x) * imageWidth, (b.y - a.y) * imageHeight)
        val width = max(length(topLeft, topRight), length(bottomLeft, bottomRight))
        val height = max(length(topLeft, bottomLeft), length(topRight, bottomRight))
        return ImageSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1))
    }

    private fun isConvex(): Boolean {
        val points = corners
        return points.indices.all { i ->
            val a = points[i]
            val b = points[(i + 1) % 4]
            val c = points[(i + 2) % 4]
            // Clockwise on a y-down image means every turn is positive.
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > 0f
        }
    }

    private fun shortestSide(): Float = corners.indices.minOf { i ->
        val a = corners[i]
        val b = corners[(i + 1) % 4]
        hypot(b.x - a.x, b.y - a.y)
    }

    companion object {
        const val MIN_SIDE = 0.05f

        /** The starting selection: the whole photo, pulled in so the handles are easy to grab. */
        fun inset(fraction: Float = 0.05f): PageQuad {
            val near = fraction
            val far = 1f - fraction
            return PageQuad(
                topLeft = PagePoint(near, near),
                topRight = PagePoint(far, near),
                bottomRight = PagePoint(far, far),
                bottomLeft = PagePoint(near, far),
            )
        }
    }
}

/** Where a `ContentScale.Fit` image lands inside its box, in box pixels. */
data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun toBox(point: PagePoint): Pair<Float, Float> =
        left + point.x * width to top + point.y * height

    fun toImage(x: Float, y: Float): PagePoint =
        PagePoint((x - left) / width, (y - top) / height)

    /** The corner of [quad] within [radius] box pixels of ([x], [y]), nearest first. */
    fun cornerNear(quad: PageQuad, x: Float, y: Float, radius: Float): PageCorner? =
        PageCorner.entries
            .map { corner ->
                val (cx, cy) = toBox(quad[corner])
                corner to hypot(cx - x, cy - y)
            }
            .filter { (_, distance) -> distance <= radius }
            .minByOrNull { (_, distance) -> distance }
            ?.first

    companion object {
        fun of(imageWidth: Int, imageHeight: Int, boxWidth: Float, boxHeight: Float): FitRect {
            val scale = minOf(boxWidth / imageWidth, boxHeight / imageHeight)
            val width = imageWidth * scale
            val height = imageHeight * scale
            return FitRect((boxWidth - width) / 2f, (boxHeight - height) / 2f, width, height)
        }
    }
}
