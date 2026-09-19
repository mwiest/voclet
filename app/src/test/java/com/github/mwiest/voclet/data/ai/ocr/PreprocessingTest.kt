package com.github.mwiest.voclet.data.ai.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the arithmetic that decides what pixels the two models ever see,
 * against the sizes RapidOCR computes for the same inputs.
 *
 * Worth a test of its own because nothing downstream complains when it is
 * wrong: a page resized to the wrong shape, or a crop padded to the wrong
 * width, still runs and still returns text — just worse text.
 */
class PreprocessingTest {

    @Test
    fun `the page is resized the way RapidOCR resizes it`() {
        for (line in fixture("det-resize.tsv")) {
            val (width, height, expectedWidth, expectedHeight) = line.split("\t").map { it.toInt() }
            assertEquals(
                "${width}x$height",
                ImageSize(expectedWidth, expectedHeight),
                DetectorInput.networkSize(ImageSize(width, height)),
            )
        }
    }

    @Test
    fun `the short side sets the scale, so a page usually grows`() {
        // 736 is a floor on the short side, not a cap on the long one. Reading
        // it as a cap would quietly shrink every page to a third of its size.
        assertEquals(ImageSize(992, 736), DetectorInput.networkSize(ImageSize(640, 480)))
        assertEquals(ImageSize(1216, 1600), DetectorInput.networkSize(ImageSize(1200, 1600)))
    }

    @Test
    fun `every crop is cut and batched the way RapidOCR does it`() {
        val expected = fixture("rec-plan.tsv").map { it.split("\t") }
        assertEquals(293, expected.size)

        for ((page, rows) in expected.groupBy { it[0] }) {
            val plans = RecognizerInput.plan(quads(page))
            assertEquals("$page: wrong number of crops", rows.size, plans.size)

            for ((row, plan) in rows.zip(plans)) {
                val where = "$page crop ${row[1]}"
                assertEquals("$where: quad index", row[1].toInt(), plan.quadIndex)
                assertEquals("$where: width", row[2].toInt(), plan.size.width)
                assertEquals("$where: height", row[3].toInt(), plan.size.height)
                assertEquals("$where: rotated", row[4] == "1", plan.rotated)
                assertEquals("$where: batch", row[5].toInt(), plan.batch)
                assertEquals("$where: padded width", row[6].toInt(), plan.paddedWidth)
                assertEquals("$where: resized width", row[7].toInt(), plan.resizedWidth)
            }
        }
    }

    @Test
    fun `a quad taller than it is wide is turned upright`() {
        val upright = Quad(Pt(0, 0), Pt(100, 0), Pt(100, 40), Pt(0, 40))
        assertEquals(ImageSize(100, 40) to false, RecognizerInput.cropSize(upright))

        val onItsSide = Quad(Pt(0, 0), Pt(40, 0), Pt(40, 100), Pt(0, 100))
        assertEquals(ImageSize(100, 40) to true, RecognizerInput.cropSize(onItsSide))
    }

    @Test
    fun `normalization matches the two models' different expectations`() {
        // The recognizer wants -1..1, the detector ImageNet statistics.
        assertEquals(-1f, Normalization.recognizer(0), 1e-6f)
        assertEquals(1f, Normalization.recognizer(255), 1e-6f)
        assertEquals((0f - 0.485f) / 0.229f, Normalization.detector(0, 0), 1e-6f)
        assertEquals((1f - 0.406f) / 0.225f, Normalization.detector(255, 2), 1e-6f)
    }

    @Test
    fun `the ncnn mean and norm restate the same two transforms`() {
        // ncnn normalizes for us, as (value - mean) * norm over the raw bytes.
        // Getting it wrong degrades the reading instead of failing, so the
        // restatement is pinned against the scalar definitions rather than
        // trusted. Both forms, every channel, both ends of the range.
        for (channel in 0..2) {
            for (value in intArrayOf(0, 1, 127, 128, 254, 255)) {
                assertEquals(
                    "detector channel $channel at $value",
                    Normalization.detector(value, channel),
                    (value - Normalization.DETECTOR_NCNN_MEAN[channel]) *
                        Normalization.DETECTOR_NCNN_NORM[channel],
                    1e-5f,
                )
                assertEquals(
                    "recognizer channel $channel at $value",
                    Normalization.recognizer(value),
                    (value - Normalization.RECOGNIZER_NCNN_MEAN[channel]) *
                        Normalization.RECOGNIZER_NCNN_NORM[channel],
                    1e-5f,
                )
            }
        }
        // A byte cannot hold 127.5, so the pad is half a step off normalized
        // zero. Worth knowing, and small enough not to matter.
        assertEquals(0f, Normalization.recognizer(Normalization.RECOGNIZER_PAD), 0.005f)
    }

    private fun fixture(name: String): List<String> =
        requireNotNull(javaClass.getResourceAsStream("/ocr/$name")) { "missing /ocr/$name" }
            .bufferedReader().readLines().filter { it.isNotBlank() }

    private fun quads(page: String): List<Quad> =
        fixture("$page.detboxes.tsv").map { line ->
            val v = line.split("\t").map { it.trim().toInt() }
            Quad(Pt(v[0], v[1]), Pt(v[2], v[3]), Pt(v[4], v[5]), Pt(v[6], v[7]))
        }
}
