package com.github.mwiest.voclet.data.ai.ocr

import java.text.Normalizer

/**
 * A port of `vbench.py`'s `score_page`, so the Kotlin pipeline can be graded
 * against `tools/llm-bench/images/<page>.json` in the same terms the bench reports.
 *
 * Only used by [GeometryPairingTest]. It is here rather than in the bench
 * because a number that cannot be checked from the app is a number nobody
 * checks.
 */
object PageScoring {

    /**
     * @param read pairs matched to the page at all
     * @param exact matched with every character right
     * @param swapped matched only with the two columns exchanged — the
     *   invariant that must stay at zero
     * @param junk pairs returned that are on no row of the page
     */
    data class Score(
        val read: Int,
        val exact: Int,
        val swapped: Int,
        val junk: Int,
        val of: Int,
        val misses: List<Pair<String, String>>,
        val junkPairs: List<Pair<String, String>>,
    )

    private val articles = mapOf(
        "de" to setOf(
            "der", "die", "das", "den", "dem", "des",
            "ein", "eine", "einen", "einem", "einer",
        ),
        "en" to setOf("the", "a", "an"),
        "fr" to setOf("le", "la", "les", "un", "une", "des"),
        "es" to setOf("el", "la", "los", "las", "un", "una", "unos", "unas"),
    )

    /**
     * Typographic characters a printed page uses and a reader may return
     * either way. Which of ' and ’ is on the page is not something even a human
     * transcriber can tell from a photo, so it cannot decide whether an answer
     * counts.
     */
    private val typographic = mapOf(
        '‘' to "'", '’' to "'", '‛' to "'",
        '“' to "\"", '”' to "\"", '„' to "\"",
        '–' to "-", '—' to "-", '…' to "...", ' ' to " ",
    )

    private const val QUOTES = "\"'`“”‘’„»«"
    private const val EDGE_PUNCTUATION = " .,;:!¡"

    /** Greedy one-to-one match of the pairs read against the pairs on the page. */
    fun score(
        got: List<Pair<String, String>>,
        truth: List<Pair<String, String>>,
        lang1: String,
        lang2: String,
    ): Score {
        val keys = got.map { key(it.first, lang1) to key(it.second, lang2) }
        val swappedKeys = got.map { key(it.second, lang1) to key(it.first, lang2) }

        val used = mutableSetOf<Int>()
        var read = 0
        var exact = 0
        var swapped = 0
        val misses = mutableListOf<Pair<String, String>>()

        for ((want1, want2) in truth) {
            val want = key(want1, lang1) to key(want2, lang2)

            val hit = firstUnused(keys, want, used)
            if (hit != null) {
                used.add(hit)
                read++
                if (unify(got[hit].first) == unify(want1) && unify(got[hit].second) == unify(want2)) {
                    exact++
                }
                continue
            }
            val swapHit = firstUnused(swappedKeys, want, used)
            if (swapHit != null) {
                used.add(swapHit)
                read++
                swapped++
                continue
            }
            // Right word, wrong diacritics: a misread, but one letter away.
            val stripped = stripAccents(want.first) to stripAccents(want.second)
            val accentHit = keys.indices.firstOrNull {
                it !in used &&
                    stripAccents(keys[it].first) == stripped.first &&
                    stripAccents(keys[it].second) == stripped.second
            }
            if (accentHit != null) used.add(accentHit)
            misses.add(want1 to want2)
        }

        val junk = got.filterIndexed { i, _ -> i !in used }
        return Score(read, exact, swapped, junk.size, truth.size, misses, junk)
    }

    private fun firstUnused(
        keys: List<Pair<String, String>>,
        want: Pair<String, String>,
        used: Set<Int>,
    ): Int? = keys.indices.firstOrNull { it !in used && keys[it] == want }

    /**
     * What counts as the same entry: case, edge punctuation, inner whitespace,
     * the article and the English `to` are all forgiven, because a row read
     * with the wrong article is a different failure from a row not read at all.
     */
    private fun key(text: String, lang: String): String {
        var out = unify(text).replace(Regex("\\s+"), " ").trim()
        out = out.trim { it in QUOTES }.trim { it in EDGE_PUNCTUATION }.lowercase()
        val first = out.substringBefore(' ')
        if (first.isNotEmpty() && first in articles[lang].orEmpty()) {
            out = out.drop(first.length).trim()
        }
        if (lang == "en") out = out.replace(Regex("^to\\s+"), "")
        return out
    }

    private fun unify(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC)
            .map { typographic[it] ?: it.toString() }
            .joinToString("")

    private fun stripAccents(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
