package com.github.mwiest.voclet.data.ai.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

/**
 * Holds [DbPostProcess] against RapidOCR's own `DBPostProcess`, over the real
 * detector output for the four bench pages (`src/test/resources/ocr`, see the
 * README there).
 *
 * The fixtures are the probability map the ONNX detector emitted, quantized to
 * a byte per pixel — verified on the host to move no box on any of these pages,
 * which is what lets 8 MB of floats become 24 KB of fixture. The expected boxes
 * are the *float* pipeline's, so a pass means the Kotlin reproduces the real
 * thing rather than the compressed copy of it.
 *
 * **Why the match is to within a pixel rather than exact.** 281 of the 293
 * boxes land on upstream's coordinates exactly; the other 12 sit one pixel
 * away, always one. A near-square text blob has two orientations of almost
 * equal area, and OpenCV's rotating calipers and this port's edge sweep can
 * settle that tie on different edges. The rectangles stay the same size — it is
 * the truncation onto Clipper's integer grid that turns a fractional difference
 * into a whole pixel. Boxes carry ~25 px of deliberate padding, so a pixel
 * cannot change what the recognizer reads; a *second* pixel would mean
 * something else is wrong, which is what [MAX_DRIFT_PX] is watching for.
 */
class DbPostProcessTest {

    private val pages = listOf("clean-de-en", "fr-de-fullpage", "fr-de-simple", "glossary-de-en")

    /** No box may move further than this from the one RapidOCR produced. */
    private val MAX_DRIFT_PX = 1

    /** How many of the 293 land exactly, today. */
    private val expectedExact = 281

    @Test
    fun `every page reproduces the boxes RapidOCR detected`() {
        val report = StringBuilder()
        var exact = 0
        var total = 0
        for (page in pages) {
            val map = probabilityMap(page)
            val expected = expectedBoxes(page)
            val got = DbPostProcess.detect(
                map.probabilities,
                map.width,
                map.height,
                map.sourceWidth,
                map.sourceHeight,
            ).map { it.corners }.sortedWith(boxOrder)

            assertEquals("$page: wrong number of boxes", expected.size, got.size)

            // Matched nearest-first, not by position: a box that moves a pixel
            // also moves in the sort order, which would misreport every box
            // after it as wildly wrong.
            val unmatched = got.toMutableList()
            val drift = expected.map { box ->
                val nearest = unmatched.minBy { drift(box, it) }
                unmatched.remove(nearest)
                drift(box, nearest)
            }
            exact += drift.count { it == 0 }
            total += drift.size
            report.append(
                "\n  %-16s %3d boxes, %3d exact, worst drift %d px"
                    .format(page, expected.size, drift.count { it == 0 }, drift.max()),
            )
            assertTrue(
                "$page: a box moved ${drift.max()} px, more than $MAX_DRIFT_PX$report",
                drift.max() <= MAX_DRIFT_PX,
            )
        }

        assertEquals("boxes graded$report", 293, total)
        assertTrue(
            "fewer boxes land exactly than before: $exact of $total, was $expectedExact$report",
            exact >= expectedExact,
        )
    }

    @Test
    fun `an empty map detects nothing`() {
        assertEquals(emptyList<Quad>(), DbPostProcess.detect(FloatArray(64 * 64), 64, 64, 64, 64))
    }

    @Test
    fun `a single upright block is found where it was drawn`() {
        val width = 200
        val height = 100
        val probabilities = FloatArray(width * height)
        for (y in 40 until 60) {
            for (x in 50 until 150) probabilities[y * width + x] = 1f
        }

        val quads = DbPostProcess.detect(probabilities, width, height, width, height)
        assertEquals(1, quads.size)
        // The box is grown by area*1.6/perimeter, so it sits outside the drawn
        // block on every side rather than on it.
        val quad = quads.single()
        assertEquals(true, quad.topLeft.x < 50 && quad.topLeft.y < 40)
        assertEquals(true, quad.bottomRight.x > 150 && quad.bottomRight.y > 60)
    }

    private data class ProbabilityMap(
        val probabilities: FloatArray,
        val width: Int,
        val height: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
    )

    /** `<mapW> <mapH> <srcW> <srcH>\n` then one byte per pixel, gzipped. */
    private fun probabilityMap(page: String): ProbabilityMap {
        val stream = requireNotNull(javaClass.getResourceAsStream("/ocr/$page.probmap.gz")) {
            "missing fixture /ocr/$page.probmap.gz"
        }
        GZIPInputStream(stream).use { gzip ->
            val header = StringBuilder()
            while (true) {
                val byte = gzip.read()
                if (byte < 0 || byte == '\n'.code) break
                header.append(byte.toChar())
            }
            val (width, height, sourceWidth, sourceHeight) =
                header.toString().trim().split(" ").map { it.toInt() }

            val bytes = ByteArray(width * height)
            DataInputStream(gzip).readFully(bytes)
            val probabilities = FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
            return ProbabilityMap(probabilities, width, height, sourceWidth, sourceHeight)
        }
    }

    /** One box per line: eight integers, clockwise from the top left. */
    private fun expectedBoxes(page: String): List<List<Pt>> {
        val text = requireNotNull(javaClass.getResourceAsStream("/ocr/$page.detboxes.tsv")) {
            "missing fixture /ocr/$page.detboxes.tsv"
        }.bufferedReader().readText()

        return text.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val values = line.split("\t").map { it.trim().toInt() }
                (0 until 4).map { Pt(values[2 * it], values[2 * it + 1]) }
            }
            .toList()
            .sortedWith(boxOrder)
    }

    /** Largest distance between two boxes' matching corners, in pixels. */
    private fun drift(a: List<Pt>, b: List<Pt>): Int =
        a.zip(b).maxOf { (p, q) -> maxOf(abs(p.x - q.x), abs(p.y - q.y)) }

    private val boxOrder: Comparator<List<Pt>> = Comparator { a, b ->
        a.asSequence().zip(b.asSequence())
            .map { (p, q) -> compareValuesBy(p, q, { it.x }, { it.y }) }
            .firstOrNull { it != 0 } ?: 0
    }
}
