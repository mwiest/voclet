package com.github.mwiest.voclet.data.ai.local

/**
 * Prompt templates for on-device inference. Kept here so they're easy to
 * iterate on without touching engine logic.
 *
 * These go through the model's own chat template (see `LlamaLlmEngine`), so they
 * are the user turn only — no role markers here. Small local models follow
 * short, literal instructions far better than verbose ones, and a one-line
 * answer is also the fastest: every token generated costs the same on a phone
 * CPU, so a prompt that invites prose is a prompt that invites a timeout.
 */
object LlmPrompts {

    /** Asks for a comma-separated list of translations, most common first. */
    fun translation(word: String, fromLang: String, toLang: String): String =
        "Translate from $fromLang to $toLang: \"$word\"\n" +
            "Answer with the $toLang translation only, or a few comma-separated " +
            "alternatives with the most common first. No sentence, no explanation."

    /** Asks for a compact JSON array of word pairs extracted from an image. */
    fun imageExtraction(lang1: String?, lang2: String?): String {
        val l1 = lang1 ?: "first"
        val l2 = lang2 ?: "second"
        return "This image is a vocabulary list of word pairs.\n" +
            "Answer with a JSON array only, no markdown and no commentary:\n" +
            "[{\"word1\":\"...\",\"word2\":\"...\"}]\n" +
            "word1 is the $l1-language term, word2 the $l2-language term. " +
            "Include every pair you can read."
    }
}
