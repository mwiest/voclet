package com.github.mwiest.voclet.data.ai.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import java.io.Closeable
import java.io.File

/**
 * Reads a page with PP-OCRv5: the detector finds the text lines, the Latin
 * recognizer reads each one. Neither model knows the page's language, which is
 * why nothing here takes one.
 *
 * The output is what [GeometryPairing] consumes, so the whole photo-import path
 * is `bitmap -> read() -> GeometryPairing.pairUp(wholeCells = true)`.
 *
 * Both models are held open for the life of the instance and released by
 * [close]; loading them is the expensive part, reading a page is not.
 */
class PageReader private constructor(
    private val detector: NcnnNet,
    private val recognizer: NcnnNet,
    private val decoder: CtcDecoder,
    private val alphabetSize: Int,
) : Closeable {

    /**
     * Every text line on [page], in the page's own pixel coordinates.
     *
     * Lines the recognizer is not confident about are dropped rather than
     * returned as doubtful text: on this page a pair the user has to find and
     * delete costs more than one they have to type.
     */
    fun read(page: Bitmap): List<TextBox> {
        val quads = detect(page)
        if (quads.isEmpty()) return emptyList()
        return recognize(page, quads)
    }

    private fun detect(page: Bitmap): List<Quad> {
        val target = DetectorInput.networkSize(ImageSize(page.width, page.height))
        val scaled = page.scaledTo(target)
        try {
            val map = detector.run(
                scaled.toRgb(),
                target.width,
                target.height,
                Normalization.DETECTOR_NCNN_MEAN,
                Normalization.DETECTOR_NCNN_NORM,
            )
            check(map.channels == 1) {
                "detector returned ${map.channels} channels, expected a single-channel map"
            }
            return DbPostProcess.detect(
                map.values,
                map.width,
                map.height,
                page.width,
                page.height,
            )
        } finally {
            if (scaled !== page) scaled.recycle()
        }
    }

    /**
     * One crop at a time: the converted model takes a batch of one.
     *
     * That costs nothing, because batched inference never mixed the samples —
     * the only thing a batch did was pad every crop to the widest member's
     * width, and [RecognizerInput.plan] still works out that width so each crop
     * is padded to exactly what the batch would have given it.
     */
    private fun recognize(page: Bitmap, quads: List<Quad>): List<TextBox> {
        val read = arrayOfNulls<Recognition>(quads.size)

        for (plan in RecognizerInput.plan(quads)) {
            val crop = cropUpright(page, quads[plan.quadIndex], plan)
            val probabilities = try {
                recognizer.run(
                    crop.toRgb(),
                    plan.paddedWidth,
                    RecognizerInput.HEIGHT,
                    Normalization.RECOGNIZER_NCNN_MEAN,
                    Normalization.RECOGNIZER_NCNN_NORM,
                )
            } finally {
                crop.recycle()
            }
            // The class list ships beside the weights rather than inside them,
            // so this is the only moment the two can be checked against each
            // other. A mismatch shifts every character and nothing else errors.
            check(probabilities.width == alphabetSize) {
                "recognizer emits ${probabilities.width} classes, dictionary has $alphabetSize"
            }
            read[plan.quadIndex] = decoder.decode(probabilities.values, probabilities.height)
        }

        return quads.indices.mapNotNull { index ->
            val recognition = read[index] ?: return@mapNotNull null
            if (recognition.confidence < MIN_TEXT_SCORE) return@mapNotNull null
            val text = recognition.text.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
            if (text.isEmpty()) return@mapNotNull null
            quads[index].toTextBox(text)
        }
    }

    /**
     * The quad, cut out, squared up and padded to its batch width.
     *
     * A perspective transform does the crop, the rotation and the de-skew in
     * one step, which is what lets a page photographed slightly askew be read
     * without deskewing the whole page.
     */
    private fun cropUpright(page: Bitmap, quad: Quad, plan: CropPlan): Bitmap {
        val width = if (plan.rotated) plan.size.height else plan.size.width
        val height = if (plan.rotated) plan.size.width else plan.size.height

        val source = FloatArray(8)
        quad.corners.forEachIndexed { index, corner ->
            source[2 * index] = corner.x.toFloat()
            source[2 * index + 1] = corner.y.toFloat()
        }
        val destination = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat(),
        )

        val square = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(square).drawBitmap(
            page,
            Matrix().apply { setPolyToPoly(source, 0, destination, 0, 4) },
            Paint(Paint.FILTER_BITMAP_FLAG),
        )

        val upright = if (plan.rotated) square.rotatedQuarterTurn() else square
        if (upright !== square) square.recycle()

        val scaled = Bitmap.createScaledBitmap(
            upright,
            plan.resizedWidth,
            RecognizerInput.HEIGHT,
            true,
        )
        if (scaled !== upright) upright.recycle()
        if (plan.resizedWidth == plan.paddedWidth) return scaled

        val padded = Bitmap.createBitmap(
            plan.paddedWidth,
            RecognizerInput.HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val pad = Normalization.RECOGNIZER_PAD
        Canvas(padded).apply {
            drawColor(Color.rgb(pad, pad, pad))
            drawBitmap(scaled, 0f, 0f, null)
        }
        scaled.recycle()
        return padded
    }

    override fun close() {
        detector.close()
        recognizer.close()
    }

    companion object {
        /** Below this mean character probability, a line is dropped unread. */
        const val MIN_TEXT_SCORE = 0.5f

        private val WHITESPACE = Regex("\\s+")

        fun open(
            detectorParam: File,
            detectorWeights: File,
            recognizerParam: File,
            recognizerWeights: File,
            dictionary: List<String>,
        ): PageReader {
            val alphabet = CtcDecoder.alphabetFrom(dictionary)
            val detector = NcnnNet.open(detectorParam, detectorWeights)
            val recognizer = try {
                NcnnNet.open(recognizerParam, recognizerWeights)
            } catch (failure: Throwable) {
                detector.close()
                throw failure
            }
            return PageReader(detector, recognizer, CtcDecoder(alphabet), alphabet.size)
        }
    }
}

private fun Bitmap.scaledTo(size: ImageSize): Bitmap =
    if (size.width == width && size.height == height) this
    else Bitmap.createScaledBitmap(this, size.width, size.height, true)

/**
 * The bitmap as three interleaved bytes a pixel, which is what ncnn takes.
 *
 * RGB, not BGR. RapidOCR decodes with PIL where PaddleOCR would use OpenCV, and
 * the 129/136 the port is held to was measured through PIL - feeding BGR
 * instead changes a line or four on every page.
 */
private fun Bitmap.toRgb(): ByteArray {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val bytes = ByteArray(pixels.size * 3)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        bytes[3 * index] = ((pixel shr 16) and 0xFF).toByte()
        bytes[3 * index + 1] = ((pixel shr 8) and 0xFF).toByte()
        bytes[3 * index + 2] = (pixel and 0xFF).toByte()
    }
    return bytes
}

/** Counter-clockwise, matching numpy's `rot90` in `get_rotate_crop_image`. */
private fun Bitmap.rotatedQuarterTurn(): Bitmap =
    Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(-90f) }, true)

private fun Quad.toTextBox(text: String): TextBox {
    val xs = corners.map { it.x }
    val ys = corners.map { it.y }
    return TextBox(
        text = text,
        x = xs.min(),
        y = ys.min(),
        width = xs.max() - xs.min(),
        height = ys.max() - ys.min(),
    )
}
