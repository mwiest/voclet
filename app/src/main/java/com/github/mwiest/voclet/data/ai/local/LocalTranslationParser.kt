package com.github.mwiest.voclet.data.ai.local

import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion

/**
 * Parses a local model's free-form translation output (which we prompt to be a
 * comma-separated list, most common first) into a [TranslationSuggestion].
 */
object LocalTranslationParser {
    private const val MAX_ALTERNATIVES = 3

    fun parse(raw: String): TranslationSuggestion? {
        val parts = raw.split(',', '\n', ';')
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
