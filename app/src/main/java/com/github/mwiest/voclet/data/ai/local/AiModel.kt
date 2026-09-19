package com.github.mwiest.voclet.data.ai.local

/**
 * Hardware tier a model targets. Used both to label models and to derive the
 * device's suggested tier from available RAM (see [DeviceHardware]).
 */
enum class ModelTier { LOW, MID, HIGH }

/**
 * Metadata describing a downloadable on-device LLM.
 *
 * Text only. There was a second ladder of SmolVLM models here for reading a
 * vocabulary page; it is gone, because no vision-language model small enough to
 * ship could read one — see `.claude/tasks/photo-import-ocr.md`. Photos are read
 * by PP-OCRv5 now, which is not a language model and so is not in this catalog.
 *
 * URLs are pinned to specific file names (rather than "latest") so they don't
 * drift when the upstream repo adds new quantizations.
 */
data class AiModel(
    override val id: String,
    val tier: ModelTier,
    override val displayName: String,
    val ggufUrl: String,
    val ggufFileName: String,
    /**
     * Exact on-disk size of the weights file, in bytes, as the HuggingFace API
     * reports it for the pinned file name.
     */
    val ggufSizeBytes: Long,
    /**
     * Total device RAM (bytes) below which this model should not be recommended.
     *
     * Derived as roughly **6x [totalSizeBytes]**, which is not a guess: llama.cpp
     * maps the weights, so resident use tracks the on-disk size closely. The
     * ratio was settled against a 1.59 GiB model that is no longer in this
     * catalog: it measured ~1.5 GiB RSS, and on a nominally 8 GB phone - 4.7x -
     * it exhausted ZRAM swap and had the low-memory killer closing background
     * apps. So 4.7x is known-too-tight and 6x is the smallest honest step past
     * it.
     *
     * Compared against `ActivityManager.MemoryInfo.totalMem`, which reports
     * *usable* RAM: an 8 GB phone reports about 7.5 GiB and a 16 GB one about
     * 15 GiB, because the kernel keeps a slice. Thresholds sit below the round
     * marketing number for that reason.
     */
    val minRamBytes: Long,
    /**
     * The turn markers this model was trained on, with [PROMPT_PLACEHOLDER]
     * where the prompt goes.
     *
     * Required, because the binding cannot supply it: `getFormattedChat` returns
     * blank for every model tried on device, even ones whose GGUF carries a
     * template upstream. So this is the only source, and each one is transcribed
     * from that model's own `tokenizer_config.json` — never written from memory
     * of "roughly ChatML". A wrong template does not fail visibly; it feeds the
     * model markers it has never seen, and the model parrots them back as its
     * answer. That is how `<end_of_utterance>` came to be offered to the user as
     * a translation of "das Tier".
     */
    val promptFormat: String,
) : DownloadBundle {

    override val files: List<BundleFile>
        get() = listOf(BundleFile(ggufUrl, ggufFileName, ggufSizeBytes))

    companion object {
        private const val GIB = 1024L * 1024L * 1024L

        /** Where the prompt goes inside a [promptFormat]. */
        const val PROMPT_PLACEHOLDER = "{prompt}"

        /**
         * Where the system instruction goes, for templates that have a system
         * turn. Optional: a template without it gets the instruction inlined
         * into the user turn instead (see `LlamaLlmEngine.formatAsChat`).
         */
        const val SYSTEM_PLACEHOLDER = "{system}"

        /**
         * ChatML, transcribed from LFM2's own `chat_template.jinja` rather than
         * from memory of "roughly ChatML".
         *
         * The system turn is where the instruction has to go — the same words
         * in the user turn cost over half the article accuracy.
         */
        private const val CHAT_ML =
            "<|im_start|>system\n$SYSTEM_PLACEHOLDER<|im_end|>\n" +
                "<|im_start|>user\n$PROMPT_PLACEHOLDER<|im_end|>\n" +
                "<|im_start|>assistant\n"

        /**
         * Text models, for translation hints. Both LFM2, so both take the same
         * [promptFormat] and the same prompt wording.
         *
         * There is deliberately no HIGH rung: every larger model measured is
         * worse at this task, not better. [DeviceHardware.suggestTierForRam]
         * reads this list, so an absent tier is simply never suggested.
         *
         * **Licence note:** LFM2 is under the LFM Open License, not Apache-2.0
         * like the rest of the catalog. It permits commercial use below a
         * revenue threshold. Worth revisiting if Voclet is ever sold.
         */
        val TEXT: List<AiModel> = listOf(
            AiModel(
                id = "lfm2-700m",
                tier = ModelTier.LOW,
                displayName = "LFM2 700M",
                ggufUrl = "https://huggingface.co/LiquidAI/LFM2-700M-GGUF/resolve/main/LFM2-700M-Q4_K_M.gguf",
                ggufFileName = "LFM2-700M-Q4_K_M.gguf",
                ggufSizeBytes = 468_624_320L,     // 447 MiB
                minRamBytes = 3 * GIB,
                promptFormat = CHAT_ML,
            ),
            AiModel(
                id = "lfm2-1.2b",
                tier = ModelTier.MID,
                displayName = "LFM2 1.2B",
                ggufUrl = "https://huggingface.co/LiquidAI/LFM2-1.2B-GGUF/resolve/main/LFM2-1.2B-Q4_K_M.gguf",
                ggufFileName = "LFM2-1.2B-Q4_K_M.gguf",
                ggufSizeBytes = 730_893_248L,     // 697 MiB
                minRamBytes = 5 * GIB,
                promptFormat = CHAT_ML,
            ),
        )

        /** Every downloadable model. */
        val ALL: List<AiModel> = TEXT

        fun byId(id: String): AiModel? = ALL.firstOrNull { it.id == id }

        fun forTier(tier: ModelTier): AiModel = ALL.first { it.tier == tier }
    }
}
