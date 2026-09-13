package com.github.mwiest.voclet.data.ai.ocr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/**
 * Grades [GeometryPairing] against real PP-OCRv5 output for four pages, held
 * in `src/test/resources/ocr` (see the README there).
 *
 * Two things are asserted, and they fail for different reasons:
 *
 * - **Parity.** Every page must produce exactly the pairs `geompair.py`
 *   produced from the same boxes. This is the port's contract, and it holds
 *   whether or not the recognizer read the page well.
 * - **The score.** Those pairs must still be worth 129 of 136 against the
 *   truth files, with **nothing swapped**. Parity alone cannot catch a
 *   re-recording of the fixtures that quietly reads a worse page.
 */
class GeometryPairingTest {

    private val pages = listOf("clean-de-en", "fr-de-fullpage", "fr-de-simple", "glossary-de-en")

    /** What `ocrbench.py -e paddle` scores over the same four pages. */
    private val expectedExact = 129
    private val expectedOf = 136

    @Test
    fun `every page reproduces the pairs geompair produced`() {
        for (page in pages) {
            val pairs = GeometryPairing.pairUp(boxes(page), wholeCells = true)
            assertEquals(
                "$page: the Kotlin port and geompair.py disagree",
                expectedPairs(page),
                pairs,
            )
        }
    }

    @Test
    fun `the four pages score what the bench scores, and nothing is swapped`() {
        var exact = 0
        var of = 0
        val report = StringBuilder()

        for (page in pages) {
            val truth = truth(page) ?: return  // tools/ absent: nothing to grade against
            val pairs = GeometryPairing.pairUp(boxes(page), wholeCells = true)
            val score = PageScoring.score(pairs, truth.pairs, truth.lang1, truth.lang2)
            exact += score.exact
            of += score.of
            report.append(
                "\n  %-16s exact %2d/%-2d  swapped %d  junk %d"
                    .format(page, score.exact, score.of, score.swapped, score.junk),
            )
            // Across four pages and four recognizers the geometry has never
            // paired the wrong cells. If this ever fires it is a port bug; do
            // not "fix" it by adding heuristics.
            assertEquals("$page: the geometry paired the wrong cells$report", 0, score.swapped)
        }

        assertEquals("pages scored$report", expectedOf, of)
        assertTrue(
            "the pipeline lost ground: $exact/$of exact, was $expectedExact/$expectedOf$report",
            exact >= expectedExact,
        )
    }

    @Test
    fun `a page with no columns yields nothing rather than guessing`() {
        val oneColumn = (0..9).map { TextBox("word$it", x = 100, y = it * 40, width = 80, height = 30) }
        assertTrue(GeometryPairing.pairUp(oneColumn, wholeCells = true).isEmpty())
        assertTrue(GeometryPairing.pairUp(emptyList(), wholeCells = true).isEmpty())
    }

    // ------------------------------------------------------------- fixtures

    /** `text<TAB>x<TAB>y<TAB>width<TAB>height`, as `paddleboxes.py` prints it. */
    private fun boxes(page: String): List<TextBox> = resource("$page.tsv").lineSequence()
        .map { it.split('\t') }
        .filter { it.size == 5 && it[0].isNotBlank() }
        .mapNotNull { (text, x, y, w, h) ->
            val box = listOf(x, y, w, h).map { it.toDoubleOrNull() ?: return@mapNotNull null }
            TextBox(
                Normalizer.normalize(text.trim(), Normalizer.Form.NFC),
                Math.round(box[0]).toInt(),
                Math.round(box[1]).toInt(),
                Math.round(box[2]).toInt(),
                Math.round(box[3]).toInt(),
            )
        }
        .toList()

    private fun expectedPairs(page: String): List<Pair<String, String>> =
        resource("$page.pairs.tsv").lineSequence()
            .map { it.split('\t') }
            .filter { it.size == 2 }
            .map { (one, two) -> one to two }
            .toList()

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/ocr/$name")) { "missing fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private class Truth(
        val lang1: String,
        val lang2: String,
        val pairs: List<Pair<String, String>>,
    )

    /** Read from the bench rather than copied, so there is one set of truth. */
    private fun truth(page: String): Truth? {
        val file = listOf("../tools/llm-bench/images/$page.json", "tools/llm-bench/images/$page.json")
            .map(::File)
            .firstOrNull { it.isFile }
            ?: return null
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        return Truth(
            lang1 = root["lang1"]?.jsonPrimitive?.content.orEmpty(),
            lang2 = root["lang2"]?.jsonPrimitive?.content.orEmpty(),
            pairs = root["pairs"]!!.jsonArray.map {
                val row = it.jsonArray
                row[0].jsonPrimitive.content.trim() to row[1].jsonPrimitive.content.trim()
            },
        )
    }
}
