package com.github.mwiest.voclet.ui.practice

import java.text.Normalizer

/**
 * Pure fuzzy matcher for the SpellIt practice mode.
 *
 * Compares the user's typed answer against the expected `word2`, handling:
 *  - Unicode NFC normalization (composed vs decomposed accents).
 *  - Stripping parenthetical/bracketed sub-expressions (e.g. "(f.)", "(pl.)").
 *  - Curly punctuation normalization (`'` → `'`, en/em dashes → `-`).
 *  - Whitespace collapsing and trailing sentence punctuation.
 *  - Multi-solution split on `/`, `,`, `;`, `|` — user's set must be a non-empty subset.
 *  - First-character case relaxation per candidate (auto-capitalization tolerance).
 *
 * Strictness: diacritics, leading "to ", and articles are preserved — the user must
 * spell them correctly. No Levenshtein typo tolerance.
 */
object SpellItMatcher {

    data class MatchResult(
        val isCorrect: Boolean,
        /**
         * On a correct match: the candidate the user matched against.
         * On a wrong answer: the *closest* expected candidate by Levenshtein distance
         * (used as the diff target). Falls back to the full normalized expected when
         * no candidates exist.
         */
        val matchedCandidate: String,
        /** Full normalized expected string (with separators preserved), for display. */
        val canonical: String
    )

    private val PAREN_PATTERN = Regex("""\([^)]*\)|\[[^\]]*]""")
    private val WHITESPACE_PATTERN = Regex("""\s+""")
    private val SEPARATOR_PATTERN = Regex("""\s*[/,;|]\s*""")
    private val TRAILING_PUNCT = setOf('.', '!', '?')

    fun matches(expected: String, userInput: String): MatchResult {
        val canonical = normalize(expected)
        val userNormalized = normalize(userInput)

        val expectedCandidates = splitCandidates(canonical)
        val userCandidates = splitCandidates(userNormalized)

        if (expectedCandidates.isEmpty()) {
            return MatchResult(isCorrect = false, matchedCandidate = canonical, canonical = canonical)
        }
        if (userCandidates.isEmpty()) {
            return MatchResult(
                isCorrect = false,
                matchedCandidate = closestCandidate(expectedCandidates, ""),
                canonical = canonical
            )
        }

        val isCorrect = userCandidates.all { user ->
            expectedCandidates.any { expected -> candidateMatches(expected, user) }
        }

        val matched = if (isCorrect) {
            // Pick first user candidate's matching expected for display.
            val first = userCandidates.first()
            expectedCandidates.first { candidateMatches(it, first) }
        } else {
            // Pick the closest expected candidate to the user's first input for diff.
            closestCandidate(expectedCandidates, userCandidates.first())
        }

        return MatchResult(isCorrect = isCorrect, matchedCandidate = matched, canonical = canonical)
    }

    /**
     * Apply the normalization pipeline to a single string.
     */
    private fun normalize(input: String): String {
        var s = Normalizer.normalize(input, Normalizer.Form.NFC)
        s = PAREN_PATTERN.replace(s, "")
        s = s.replace('‘', '\'').replace('’', '\'')
        s = s.replace('“', '"').replace('”', '"')
        s = s.replace('–', '-').replace('—', '-')
        s = WHITESPACE_PATTERN.replace(s, " ").trim()
        while (s.isNotEmpty() && s.last() in TRAILING_PUNCT) {
            s = s.dropLast(1).trimEnd()
        }
        return s
    }

    private fun splitCandidates(normalized: String): List<String> {
        if (normalized.isEmpty()) return emptyList()
        return SEPARATOR_PATTERN.split(normalized)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Strict equality except that the first character of each candidate is case-insensitive
     * (Android keyboards auto-capitalize).
     */
    private fun candidateMatches(expected: String, user: String): Boolean {
        if (expected.isEmpty() || user.isEmpty()) return expected == user
        if (expected.length != user.length) return false
        if (expected[0].lowercaseChar() != user[0].lowercaseChar()) return false
        for (i in 1 until expected.length) {
            if (expected[i] != user[i]) return false
        }
        return true
    }

    private fun closestCandidate(candidates: List<String>, user: String): String {
        return candidates.minBy { levenshtein(it, user) }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length]
    }
}
