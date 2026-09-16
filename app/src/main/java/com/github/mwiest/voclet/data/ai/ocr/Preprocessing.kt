package com.github.mwiest.voclet.data.ai.ocr

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** A width and height in pixels. */
data class ImageSize(val width: Int, val height: Int)

/**
 * What size the page is handed to the detector at.
 *
 * Counter-intuitively this usually makes the page *bigger*, not smaller:
 * [LIMIT_SIDE_LEN] is a floor on the **short** side, not a cap on the long one.
 * A 1200x1600 capture is already past it and so runs at very nearly full size —
 * which is the real answer to "how fast is a dense page", and much larger than
 * PaddleOCR's own Android timings assume.
 */
object DetectorInput {

    /** The shorter side is scaled up to at least this. */
    const val LIMIT_SIDE_LEN = 736

    /** The network's stride: both dimensions have to be a multiple of it. */
    private const val STRIDE = 32

    fun networkSize(page: ImageSize): ImageSize {
        val shortest = minOf(page.width, page.height)
        val ratio =
            if (shortest < LIMIT_SIDE_LEN) LIMIT_SIDE_LEN.toDouble() / shortest else 1.0
        return ImageSize(
            toStride(page.width * ratio),
            toStride(page.height * ratio),
        )
    }

    /**
     * Rounds to a multiple of [STRIDE], halves to even — numpy's `round`, not
     * Kotlin's. A 1131 px page becomes 1120 rather than 1152.
     */
    private fun toStride(side: Double): Int =
        max(STRIDE, (Math.rint(side.toInt() / STRIDE.toDouble()) * STRIDE).toInt())
}

/** Where one crop sits in a recognizer batch, and what it is resized to. */
data class CropPlan(
    /** Index into the detector's quads; [RecognizerInput.plan] reorders them. */
    val quadIndex: Int,
    /** The crop's size as cut from the page, after any 90° turn. */
    val size: ImageSize,
    /** The quad was half again taller than wide, so the crop is turned upright. */
    val rotated: Boolean,
    val batch: Int,
    /** Every crop in a batch is padded to this width so they tensor together. */
    val paddedWidth: Int,
    /** This crop's own width once scaled to [RecognizerInput.HEIGHT] high. */
    val resizedWidth: Int,
)

/**
 * How crops are cut and grouped for the recognizer.
 *
 * The model takes a fixed 48 px height and a free width, so a batch has to be
 * padded to its widest member. Crops are therefore **sorted by aspect ratio
 * before batching**, which puts similarly shaped lines together and keeps the
 * padding — pure wasted compute — small. It also means the recognizer's results
 * come back in an order unrelated to the page, hence [CropPlan.quadIndex].
 *
 * That sort is **stable here and is not upstream**, which is a deliberate
 * divergence. A dense page has ties (17 on the glossary), numpy's default
 * quicksort breaks them arbitrarily, and two crops land in a different batch —
 * and so get padded differently — depending on nothing but the sort's internals.
 * Forcing RapidOCR to sort stably leaves the text of all four pages unchanged,
 * so the deterministic order is free, and the fixtures are recorded with it.
 */
object RecognizerInput {

    /** The height the model is exported at; only the width is free. */
    const val HEIGHT = 48

    const val BATCH = 6

    /** Taller than this multiple of its width, a crop is a turned-on-its-side line. */
    private const val UPRIGHT_RATIO = 1.5

    /**
     * The size [quad] is cut to, and whether it is turned 90° to get there.
     *
     * A quad taller than it is wide is a line running up the page, so the crop
     * is rotated rather than squeezed into a 48 px-high letterbox.
     */
    fun cropSize(quad: Quad): Pair<ImageSize, Boolean> {
        val (topLeft, topRight, bottomRight, bottomLeft) = quad.corners
        val width = max(distance(topLeft, topRight), distance(bottomRight, bottomLeft)).toInt()
        val height = max(distance(topLeft, bottomLeft), distance(topRight, bottomRight)).toInt()
        val rotated = width > 0 && height.toDouble() / width >= UPRIGHT_RATIO
        return (if (rotated) ImageSize(height, width) else ImageSize(width, height)) to rotated
    }

    fun plan(quads: List<Quad>): List<CropPlan> {
        val crops = quads.map { cropSize(it) }
        val byShape = crops.indices.sortedBy { aspect(crops[it].first) }

        val plans = mutableListOf<CropPlan>()
        for (start in byShape.indices step BATCH) {
            val batch = byShape.subList(start, minOf(byShape.size, start + BATCH))
            val widest = batch.maxOf { aspect(crops[it].first) }
            val paddedWidth = (HEIGHT * widest).toInt()
            for (index in batch) {
                val (size, rotated) = crops[index]
                val scaled = ceil(HEIGHT * aspect(size)).toInt()
                plans += CropPlan(
                    quadIndex = index,
                    size = size,
                    rotated = rotated,
                    batch = start / BATCH,
                    paddedWidth = paddedWidth,
                    resizedWidth = if (scaled > paddedWidth) paddedWidth else scaled,
                )
            }
        }
        return plans
    }

    private fun aspect(size: ImageSize): Double =
        if (size.height == 0) 0.0 else size.width.toDouble() / size.height

    private fun distance(a: Pt, b: Pt): Double =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
}

/**
 * Pixel normalization, which differs between the two models and is silent when
 * wrong — a mismatched mean degrades the reading rather than failing it.
 */
object Normalization {

    /** The detector wants ImageNet statistics, per channel, RGB order. */
    val DETECTOR_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    val DETECTOR_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** The recognizer just wants -1..1. */
    fun recognizer(value: Int): Float = (value / 255f - 0.5f) / 0.5f

    fun detector(value: Int, channel: Int): Float =
        (value / 255f - DETECTOR_MEAN[channel]) / DETECTOR_STD[channel]
}
