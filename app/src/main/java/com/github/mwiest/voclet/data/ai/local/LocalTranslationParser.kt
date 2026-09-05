package com.github.mwiest.voclet.data.ai.local

import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion

/**
 * Parses a local model's translation output into a [TranslationSuggestion].
 *
 * The prompt asks for the best word first and any further meanings behind a
 * `|`, so the answer is just a list with the primary at the front. All four
 * separators are treated alike, which means an answer that ignores the `|`
 * convention and uses commas parses identically.
 */
object LocalTranslationParser {
    private const val MAX_ALTERNATIVES = 3

    fun parse(raw: String): TranslationSuggestion? {
        val parts = raw.split(',', '\n', ';', '|')
            .map { it.trim().trim('.', '"', '-', ' ') }
            .filter { it.isNotBlank() }
            .distinct()
        if (parts.isEmpty()) return null
        return TranslationSuggestion(
            primaryTranslation = parts.first(),
            alternatives = parts.drop(1).take(MAX_ALTERNATIVES),
            contextualNotes = null,
        )
    }
}
