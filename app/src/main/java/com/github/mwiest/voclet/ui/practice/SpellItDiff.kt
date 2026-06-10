package com.github.mwiest.voclet.ui.practice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Character-level diff between the user's input and the expected answer,
 * used to render wrong-answer feedback in SpellIt practice mode.
 *
 * Build is a Levenshtein DP table and walk the backtrace to produce a list
 * of [DiffOp]s reading left-to-right. Optimal for typical word lengths.
 */
sealed interface DiffOp {
    /** Both have this character at the same position. */
    data class Match(val char: Char) : DiffOp

    /** User typed this character but expected didn't have it (extra or wrong substitution). */
    data class Wrong(val char: Char) : DiffOp

    /** Expected had this character but user didn't type it. */
    data class Missing(val char: Char) : DiffOp
}

object SpellItDiff {

    fun diff(expected: String, userInput: String): List<DiffOp> {
        val e = expected
        val u = userInput
        val n = e.length
        val m = u.length

        if (n == 0 && m == 0) return emptyList()
        if (n == 0) return u.map { DiffOp.Wrong(it) }
        if (m == 0) return e.map { DiffOp.Missing(it) }

        // dp[i][j] = edit distance between e[0..i) and u[0..j)
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (e[i - 1] == u[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,        // delete from expected (= user missed it)
                    dp[i][j - 1] + 1,        // insert into expected (= user typed extra)
                    dp[i - 1][j - 1] + cost  // match or substitute
                )
            }
        }

        // Backtrace from (n, m) to (0, 0)
        val ops = ArrayDeque<DiffOp>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && e[i - 1] == u[j - 1] -> {
                    ops.addFirst(DiffOp.Match(e[i - 1]))
                    i--; j--
                }
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1 -> {
                    // Substitution: render user's wrong char then expected's missing char.
                    ops.addFirst(DiffOp.Missing(e[i - 1]))
                    ops.addFirst(DiffOp.Wrong(u[j - 1]))
                    i--; j--
                }
                j > 0 && (i == 0 || dp[i][j] == dp[i][j - 1] + 1) -> {
                    // User typed extra char.
                    ops.addFirst(DiffOp.Wrong(u[j - 1]))
                    j--
                }
                else -> {
                    // User missed a char.
                    ops.addFirst(DiffOp.Missing(e[i - 1]))
                    i--
                }
            }
        }
        return ops.toList()
    }

    /**
     * Render the diff as an [AnnotatedString] with per-character styling.
     *
     * @param ops the diff operations from [diff].
     * @param errorColor red color for wrong/missing characters.
     * @param matchColor color for matched characters (typically the default text color).
     */
    fun render(
        ops: List<DiffOp>,
        errorColor: Color,
        matchColor: Color
    ): AnnotatedString = buildAnnotatedString {
        for (op in ops) {
            when (op) {
                is DiffOp.Match -> withStyle(SpanStyle(color = matchColor)) {
                    append(op.char.toString())
                }
                is DiffOp.Wrong -> withStyle(
                    SpanStyle(
                        color = errorColor,
                        textDecoration = TextDecoration.LineThrough
                    )
                ) { append(op.char.toString()) }
                is DiffOp.Missing -> withStyle(
                    SpanStyle(
                        color = errorColor.copy(alpha = 0.6f),
                        textDecoration = TextDecoration.Underline,
                        fontStyle = FontStyle.Italic
                    )
                ) { append(op.char.toString()) }
            }
        }
    }
}
