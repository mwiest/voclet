package com.github.mwiest.voclet.data.ai.local

import com.github.mwiest.voclet.data.ai.LanguageNames

/**
 * Prompt templates for on-device inference. Callers pass ISO codes;
 * [LanguageNames] turns those into English names.
 *
 * `tools/llm-bench` measures these against every candidate model.
 */
object LlmPrompts {

    /**
     * A prompt split across the two turns a chat model is trained on. [system]
     * may be blank; templates without a system turn inline it rather than drop
     * it (see `LlamaLlmEngine.formatAsChat`).
     */
    data class Prompt(val system: String, val user: String)

    /**
     * Asks for one translation.
     *
     * Three things here are load-bearing and easy to undo by accident. The
     * languages must be *named* — given ISO codes the model echoes the source
     * word back. The instruction must stay on one line — split over two, the
     * model answers with the second line verbatim. And it must sit in the
     * system turn — the same words in the user turn lose over half the article
     * accuracy.
     *
     * It does not ask for alternative meanings. Models this size do not have a
     * word's second sense to retrieve and invent one when asked, so that is the
     * cloud backend's job.
     */
    fun translation(word: String, fromLang: String, toLang: String): Prompt {
        val from = LanguageNames.englishName(fromLang)
        val to = LanguageNames.englishName(toLang)
        return Prompt(
            system = "Translate the $from word into $to. Reply with only the $to " +
                "translation, all in lower case, using the infinitive form for verbs " +
                "and keeping the article for nouns.",
            user = word,
        )
    }

    /**
     * Asks for a JSON array of word pairs extracted from an image.
     *
     * It must not contain a literal `[{"word1":"...","word2":"..."}]` template.
     * Models return that template *verbatim* on a page they cannot manage — a
     * complete, parseable, empty answer, so it never looks like a prompt bug.
     * Describing the shape in words instead took InternVL3-1B from 0/15 to
     * 15/15.
     *
     * All in the user turn: the vision models' template has no system turn.
     */
    fun imageExtraction(lang1: String?, lang2: String?): Prompt {
        val l1 = lang1?.let { LanguageNames.englishName(it) } ?: "first"
        val l2 = lang2?.let { LanguageNames.englishName(it) } ?: "second"
        return Prompt(
            system = "",
            user = "Read every row of the vocabulary list in this photo.\n" +
                "Answer with a JSON array. Each element is an object with " +
                "exactly two string fields: word1 holds the $l1 term, word2 " +
                "the $l2 term on the same row.\n" +
                "Copy the words exactly as printed. Output only the array - " +
                "no markdown, no commentary, and never repeat these " +
                "instructions back.",
        )
    }
}
