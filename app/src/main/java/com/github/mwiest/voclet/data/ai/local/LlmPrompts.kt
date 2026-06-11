package com.github.mwiest.voclet.data.ai.local

/**
 * Prompt templates for on-device inference. Kept here so they're easy to
 * iterate on without touching engine logic. Small local models follow simple,
 * explicit instructions far better than verbose ones, so these are terse and
 * demand output with no surrounding prose.
 */
object LlmPrompts {

    /** Asks for a comma-separated list of translations, most common first. */
    fun translation(word: String, fromLang: String, toLang: String): String =
        """
        Translate the word or phrase "$word" from $fromLang to $toLang.
        Reply with ONLY the translation(s) in $toLang, most common first,
        separated by commas. No explanations, no extra words.
        """.trimIndent()

    /** Asks for a compact JSON array of word pairs extracted from an image. */
    fun imageExtraction(lang1: String?, lang2: String?): String {
        val l1 = lang1 ?: "first-language"
        val l2 = lang2 ?: "second-language"
        return """
        The image is a vocabulary list of word pairs.
        Extract every pair. Reply with ONLY a JSON array, no markdown fences:
        [{"word1":"...","word2":"..."}]
        word1 = the $l1 term, word2 = the $l2 term.
        """.trimIndent()
    }
}
