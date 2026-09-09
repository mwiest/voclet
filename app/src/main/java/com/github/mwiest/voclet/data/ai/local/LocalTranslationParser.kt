package com.github.mwiest.voclet.data.ai.local

import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion

/**
 * Parses a local model's translation output into a [TranslationSuggestion].
 *
 * Deliberately takes the first item only. The prompt asks for one translation,
 * so anything after a separator is the model padding — `bank, banknote` — and
 * offering that as an alternative puts an invented word one tap from the user's
 * list. Alternatives come from the cloud backend, which is asked for them.
 */
object LocalTranslationParser {

    fun parse(raw: String): TranslationSuggestion? {
        val primary = raw.split(',', '\n', ';', '|')
            .firstNotNullOfOrNull { it.trim().trim('.', '"', '-', ' ').ifBlank { null } }
            ?: return null
        return TranslationSuggestion(
            primaryTranslation = primary,
            alternatives = emptyList(),
            contextualNotes = null,
        )
    }
}
