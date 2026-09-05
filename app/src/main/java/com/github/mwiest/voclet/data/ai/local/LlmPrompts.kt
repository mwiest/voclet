package com.github.mwiest.voclet.data.ai.local

import com.github.mwiest.voclet.data.ai.LanguageNames

/**
 * Prompt templates for on-device inference. Kept here so they're easy to
 * iterate on without touching engine logic.
 *
 * These are the user turn only — no role markers. The engine wraps them in the
 * model's own turn markers (see [AiModel.promptFormat]).
 *
 * Small local models follow short, literal instructions far better than verbose
 * ones, and a one-line answer is also the fastest: every token costs the same on
 * a phone CPU, so a prompt that invites prose is a prompt that invites a
 * timeout.
 *
 * Callers pass ISO codes; [LanguageNames] turns those into English names. The
 * difference is not cosmetic — see the KDoc there.
 */
object LlmPrompts {

    /**
     * Asks for one translation. Every word of it is measured on EuroLLM 1.7B,
     * on device, against the five-word set in `PromptTuningScratchTest`.
     *
     * **Naming the languages** is what makes it translate at all — given ISO
     * codes ("translate from de to en") it echoes the source word back.
     *
     * **Ending on an empty `English:` slot** keeps the answer to a word instead
     * of a sentence repeated to the token cap.
     *
     * **The instruction stays on one line.** Not style — it decides whether the
     * model answers at all. The same words split over two lines scored 2/5
     * instead of 5/5, because the model answered with the *second line itself*:
     * `Reply with the English meanings, most common first, separated by commas.`
     * A standalone line above a `German:`/`English:` block is one more line of
     * the same document, and this thing continues documents. One line leaves
     * exactly one continuable thing below it: the empty answer slot.
     *
     * **It does not ask for alternatives, because nothing makes it give them.**
     * Thirteen phrasings were measured — a `|` separator, commas, "up to three",
     * "exactly three", "every meaning it can have", a format example, a plural
     * answer slot, dictionary framing, the source word stripped of its article,
     * and an outright assertion that the word *has* several meanings. All
     * thirteen scored 5/5 on the translation and **0/5 on alternatives**.
     * `das Schloss` is `The castle` every time.
     *
     * That is not a prompt that has not been found yet. EuroLLM is tuned for
     * translation, and it translates: instructions that ask for anything else
     * are ignored rather than obeyed, which is the same property that makes it
     * fast and 5/5 accurate. Asking anyway would only cost tokens and invite the
     * echoing above. Alternatives are the cloud backend's job.
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
