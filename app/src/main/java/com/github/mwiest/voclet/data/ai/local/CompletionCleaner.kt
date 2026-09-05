package com.github.mwiest.voclet.data.ai.local

/**
 * Strips turn scaffolding from what a local model generated, leaving the answer.
 *
 * Separate from the engine, and free of Android types, so the rules can be
 * tested against real observed output rather than only reasoned about.
 *
 * The engine cannot delegate this to the native side. `n_predict` is verified on
 * device; `stop` never was, and the evidence says it does not hold - a
 * completion ran all the way to its 24-token cap after emitting
 * `<end_of_utterance>` twice, which a working stop list would have cut at the
 * first. Whether native ignores the list or merely stops after the tokens have
 * already reached the callback, the accumulated text is ours to clean.
 */
object CompletionCleaner {

    /**
     * End-of-turn markers across the catalog's models, plus the start of a
     * hallucinated next turn. Harmless when a model does not use one.
     *
     * Passed to native as its stop list *and* applied to the text afterwards.
     * One list, both jobs, so the two can never disagree about what ends a turn.
     */
    val STOP_SEQUENCES = listOf(
        "<end_of_utterance>",
        "<end_of_turn>",
        "<|im_end|>",
        "<|endoftext|>",
        // Turn *start* markers matter as much as end ones: a model that emits
        // one has begun writing the next turn, so the answer ended before it.
        "<|im_start|>",
        "\nUser:",
    )

    /** Everything from the first stop marker on, plus any half-written marker. */
    fun clean(raw: String): String {
        var text = raw
        STOP_SEQUENCES.forEach { stop ->
            val at = text.indexOf(stop)
            if (at >= 0) text = text.substring(0, at)
        }
        // A token cap can cut a marker in half - "<end_of_" - leaving a fragment
        // no stop sequence matches and that is word-shaped enough to survive
        // every parser guard downstream. An unclosed '<' is never part of a
        // translation, so what follows it goes.
        val dangling = text.lastIndexOf('<')
        if (dangling >= 0 && !text.substring(dangling).contains('>')) {
            text = text.substring(0, dangling)
        }
        return text.trim()
    }
}
