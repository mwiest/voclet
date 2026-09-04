package com.github.mwiest.voclet.data.ai

import java.util.Locale

/**
 * Language names for AI prompts.
 *
 * The app stores languages as ISO codes, which is right for storage and wrong
 * for a prompt: asked to "translate from de to en", SmolVLM2 2.2B returned the
 * German word back with a variation of it, scoring 0/3 on device. Named in
 * English it translated correctly. Bigger cloud models cope with the codes, but
 * there is no reason to make any model guess.
 *
 * Resolution goes through [Locale] rather than a lookup table, because Voclet is
 * deliberately language-agnostic — any code the user picks has to work, not just
 * the ones in the picker.
 */
object LanguageNames {

    /**
     * The English name of the language [tagOrName] denotes ("de" to "German",
     * "en-GB" to "English"), or [tagOrName] unchanged when it is not a language
     * tag this platform knows — including when it is already a name.
     */
    fun englishName(tagOrName: String): String {
        val trimmed = tagOrName.trim()
        // Only resolve what actually looks like a code. "German" is a well-formed
        // language *subtag* as far as Locale is concerned, so it would parse and
        // come back canonically lowercased as "german".
        if (!LANGUAGE_TAG.matches(trimmed)) return trimmed

        // An unrecognized tag resolves to the root locale, whose display
        // language is empty; an ill-formed one throws on older platforms.
        val resolved = runCatching {
            Locale.forLanguageTag(trimmed.replace('_', '-')).getDisplayLanguage(Locale.ENGLISH)
        }.getOrNull()

        return resolved?.takeIf { it.isNotBlank() } ?: trimmed
    }

    /** An ISO 639 language, optionally with script/region subtags: `de`, `pt-BR`. */
    private val LANGUAGE_TAG = Regex("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*")
}
