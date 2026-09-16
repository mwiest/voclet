package com.github.mwiest.voclet.data.ai.ocr

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * Holds [CtcDecoder] against RapidOCR's `CTCLabelDecode`, over real recognizer
 * output for nine crops chosen to cover what is awkward: doubled letters, an
 * em-dash, accented characters, punctuation, and the longest, shortest and
 * least confident lines the four bench pages produced.
 *
 * The fixtures are the model's probability matrices quantized to a byte per
 * value, which was checked on the host to change none of the nine strings (the
 * matrices are almost entirely zeros, so nine of them gzip to 2 KB). The
 * recorded confidence is the *float* pipeline's, hence [CONFIDENCE_TOLERANCE]
 * — the strings, which are what matters, must match exactly.
 *
 * The alphabet comes from the shipped asset rather than a copy, so a change to
 * the dictionary that the model does not agree with fails here.
 */
class CtcDecoderTest {

    /** Quantization moves a score by at most this; measured at 0.0006. */
    private val CONFIDENCE_TOLERANCE = 0.002f

    private val decoder = CtcDecoder(CtcDecoder.alphabetFrom(dictionaryLines()))

    @Test
    fun `the alphabet is the width the model emits`() {
        assertEquals(838, CtcDecoder.alphabetFrom(dictionaryLines()).size)
    }

    @Test
    fun `every recorded crop decodes to what RapidOCR read`() {
        val samples = samples()
        assertEquals("wrong number of fixtures", 9, samples.size)

        GZIPInputStream(resource("rec-logits.gz")).use { stream ->
            val input = DataInputStream(stream)
            for (sample in samples) {
                val bytes = ByteArray(sample.timesteps * sample.classes)
                input.readFully(bytes)
                val probabilities = FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }

                val got = decoder.decode(probabilities, sample.timesteps)
                assertEquals("decoded the wrong text", sample.text, got.text)
                assertEquals(
                    "${sample.text}: confidence ${got.confidence}, recorded ${sample.confidence}",
                    true,
                    abs(got.confidence - sample.confidence) <= CONFIDENCE_TOLERANCE,
                )
            }
        }
    }

    @Test
    fun `a blank between two of the same letter keeps both`() {
        val alphabet = listOf("", "a", "b")
        val decoder = CtcDecoder(alphabet)
        assertEquals("aa", decoder.decode(oneHot(listOf(1, 0, 1), alphabet.size), 3).text)
        assertEquals("a", decoder.decode(oneHot(listOf(1, 1, 1), alphabet.size), 3).text)
        assertEquals("ab", decoder.decode(oneHot(listOf(1, 1, 2, 2), alphabet.size), 4).text)
    }

    @Test
    fun `an all-blank crop reads as nothing rather than failing`() {
        val decoder = CtcDecoder(listOf("", "a"))
        val got = decoder.decode(oneHot(listOf(0, 0, 0), 2), 3)
        assertEquals("", got.text)
        assertEquals(0f, got.confidence, 1e-6f)
    }

    private fun oneHot(winners: List<Int>, classes: Int): FloatArray =
        FloatArray(winners.size * classes).also { out ->
            winners.forEachIndexed { step, winner -> out[step * classes + winner] = 1f }
        }

    private data class Sample(
        val timesteps: Int,
        val classes: Int,
        val confidence: Float,
        val text: String,
    )

    /** `timesteps<TAB>classes<TAB>confidence<TAB>text`, matching the blob's order. */
    private fun samples(): List<Sample> =
        resource("rec-logits.tsv").bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val (timesteps, classes, confidence, text) = line.split("\t", limit = 4)
                Sample(timesteps.toInt(), classes.toInt(), confidence.toFloat(), text)
            }

    private fun resource(name: String) =
        requireNotNull(javaClass.getResourceAsStream("/ocr/$name")) { "missing fixture /ocr/$name" }

    /**
     * Read from `src/main/assets` directly. Unit tests do not see the APK's
     * assets, and a copy in test resources could drift from the shipped one —
     * which is the single failure this test most needs to catch.
     */
    private fun dictionaryLines(): List<String> {
        val asset = "src/main/assets/${CtcDecoder.DICTIONARY_ASSET}"
        val file = listOf(File(asset), File("app/$asset")).firstOrNull { it.isFile }
        return requireNotNull(file) { "cannot find $asset from ${File("").absolutePath}" }
            .readLines()
            .dropLastWhile { it.isEmpty() }
    }
}
