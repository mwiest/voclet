package com.github.mwiest.voclet.data.ai.local

import com.github.mwiest.voclet.data.ai.LanguageNames

/**
 * Prompt templates for on-device inference. Kept here so they're easy to
 * iterate on without touching engine logic.
 *
 * These go through the model's own chat template where it has one and the
 * engine's fallback otherwise (neither catalog model ships one), so they are the
 * user turn only — no role markers here. Small local models follow short,
 * literal instructions far better than verbose ones, and a one-line answer is
 * also the fastest: every token costs the same on a phone CPU, so a prompt that
 * invites prose is a prompt that invites a timeout.
 *
 * Callers pass ISO codes; [LanguageNames] turns those into English names. The
 * difference is not cosmetic — see the KDoc there.
 */
object LlmPrompts {

    /**
     * Asks for one bare translation.
     *
     * Every clause here was measured on SmolVLM2 2.2B (`TranslationPromptTest`).
     * Naming the languages is what makes it translate at all. Ending on an empty
     * `English:` slot is what keeps the answer to a single word instead of
     * `Haus - English translation: house` repeated to the token cap.
     *
     * Notably it does *not* invite alternatives: adding "or a few comma-separated
     * options" dropped it from 3/3 to 1/3, answering `Haus` for `Haus`. Small
     * models spend the invitation on listing rather than translating, and one
     * right answer beats three wrong ones. [LocalTranslationParser] still splits
     * on commas, so alternatives the model volunteers are kept.
     */
    fun translation(word: String, fromLang: String, toLang: String): String {
        val from = LanguageNames.englishName(fromLang)
        val to = LanguageNames.englishName(toLang)
        return "Translate this $from word into $to. Reply with only the $to word.\n" +
            "$from: $word\n" +
            "$to:"
    }

    /** Asks for a compact JSON array of word pairs extracted from an image. */
    fun imageExtraction(lang1: String?, lang2: String?): String {
        val l1 = lang1?.let { LanguageNames.englishName(it) } ?: "first"
        val l2 = lang2?.let { LanguageNames.englishName(it) } ?: "second"
        return "This image is a vocabulary list of word pairs.\n" +
            "Answer with a JSON array only, no markdown and no commentary:\n" +
            "[{\"word1\":\"...\",\"word2\":\"...\"}]\n" +
            "word1 is the $l1 term, word2 the $l2 term. " +
            "Include every pair you can read."
    }
}
