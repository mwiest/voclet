package com.github.mwiest.voclet.data.ai.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer

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
    private val environment: OrtEnvironment,
    private val detector: OrtSession,
    private val recognizer: OrtSession,
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
            val input = FloatArray(3 * target.height * target.width)
            val plane = target.height * target.width
            forEachPixel(scaled) { index, red, green, blue ->
                input[index] = Normalization.detector(red, 0)
                input[plane + index] = Normalization.detector(green, 1)
                input[2 * plane + index] = Normalization.detector(blue, 2)
            }

            val shapeAndMap = run(
                detector,
                input,
                longArrayOf(1, 3, target.height.toLong(), target.width.toLong()),
            )
            val (shape, probabilities) = shapeAndMap
            check(shape.size == 4 && shape[1] == 1L) {
                "detector returned ${shape.joinToString("x")}, expected a single-channel map"
            }
            return DbPostProcess.detect(
                probabilities,
                shape[3].toInt(),
                shape[2].toInt(),
                page.width,
                page.height,
            )
        } finally {
            if (scaled !== page) scaled.recycle()
        }
    }

    private fun recognize(page: Bitmap, quads: List<Quad>): List<TextBox> {
        val read = arrayOfNulls<Recognition>(quads.size)

        for ((_, batch) in RecognizerInput.plan(quads).groupBy { it.batch }) {
            val width = batch.first().paddedWidth
            val plane = RecognizerInput.HEIGHT * width
            // Left unwritten, the tail of each row stays zero, which is the
            // padding upstream adds explicitly.
            val input = FloatArray(batch.size * 3 * plane)

            batch.forEachIndexed { row, plan ->
                val crop = cropUpright(page, quads[plan.quadIndex], plan)
                try {
                    val base = row * 3 * plane
                    forEachPixel(crop) { index, red, green, blue ->
                        val at = base + (index / plan.resizedWidth) * width +
                            (index % plan.resizedWidth)
                        input[at] = Normalization.recognizer(red)
                        input[at + plane] = Normalization.recognizer(green)
                        input[at + 2 * plane] = Normalization.recognizer(blue)
                    }
                } finally {
                    crop.recycle()
                }
            }

            val (shape, probabilities) = run(
                recognizer,
                input,
                longArrayOf(batch.size.toLong(), 3, RecognizerInput.HEIGHT.toLong(), width.toLong()),
            )
            val timesteps = shape[1].toInt()
            // The class list ships beside the weights rather than inside them,
            // so this is the only moment the two can be checked against each
            // other. A mismatch shifts every character and nothing else errors.
            check(shape[2].toInt() == alphabetSize) {
                "recognizer emits ${shape[2]} classes, dictionary has $alphabetSize"
            }

            val perCrop = timesteps * alphabetSize
            batch.forEachIndexed { row, plan ->
                read[plan.quadIndex] = decoder.decode(
                    probabilities.copyOfRange(row * perCrop, (row + 1) * perCrop),
                    timesteps,
                )
            }
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
     * The quad, cut out and squared up. A perspective transform does the crop,
     * the rotation and the de-skew in one step, which is what lets a page
     * photographed slightly askew be read without deskewing the whole page.
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
        return scaled
    }

    private fun run(
        session: OrtSession,
        input: FloatArray,
        shape: LongArray,
    ): Pair<LongArray, FloatArray> {
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val output = result.get(0) as OnnxTensor
                val values = FloatArray(output.info.shape.fold(1L) { a, b -> a * b }.toInt())
                output.floatBuffer.get(values)
                return output.info.shape to values
            }
        }
    }

    override fun close() {
        detector.close()
        recognizer.close()
    }

    private inline fun forEachPixel(bitmap: Bitmap, write: (Int, Int, Int, Int) -> Unit) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            // RGB, not BGR. RapidOCR decodes with PIL where PaddleOCR would use
            // OpenCV, and the 129/136 the port is held to was measured through
            // PIL - feeding BGR instead changes a line or four on every page.
            write(index, (pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)
        }
    }

    companion object {
        /** Below this mean character probability, a line is dropped unread. */
        const val MIN_TEXT_SCORE = 0.5f

        private val WHITESPACE = Regex("\\s+")

        fun open(
            detectorModel: File,
            recognizerModel: File,
            dictionary: List<String>,
        ): PageReader {
            val environment = OrtEnvironment.getEnvironment()
            val alphabet = CtcDecoder.alphabetFrom(dictionary)
            val detector = environment.createSession(
                detectorModel.absolutePath,
                OrtSession.SessionOptions(),
            )
            val recognizer = try {
                environment.createSession(
                    recognizerModel.absolutePath,
                    OrtSession.SessionOptions(),
                )
            } catch (failure: Throwable) {
                detector.close()
                throw failure
            }
            return PageReader(environment, detector, recognizer, CtcDecoder(alphabet), alphabet.size)
        }
    }
}

private fun Bitmap.scaledTo(size: ImageSize): Bitmap =
    if (size.width == width && size.height == height) this
    else Bitmap.createScaledBitmap(this, size.width, size.height, true)

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
