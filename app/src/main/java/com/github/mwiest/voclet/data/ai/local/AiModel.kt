package com.github.mwiest.voclet.data.ai.local

/**
 * Hardware tier a model targets. Used both to label models and to derive the
 * device's suggested tier from available RAM (see [DeviceHardware]).
 */
enum class ModelTier { LOW, MID, HIGH }

/**
 * Which feature a model serves.
 *
 * The two features want opposite things from a model. Translation needs to know
 * the *languages*, and the best small multilingual models are text-only.
 * Reading a vocabulary page needs a vision projector, and the small models that
 * have one are built on English-first language backbones - SmolVLM 256M carries
 * SmolLM2-135M, SmolVLM2 2.2B carries SmolLM2-1.7B - which is precisely why
 * translating through them echoed the source word back (`Haus` for `Haus`).
 *
 * Splitting them lets each side be chosen on its own merits, and stops a user
 * who only ever types words from downloading a 592 MB vision projector to do it.
 */
enum class ModelKind { TEXT, VISION }

/**
 * Metadata describing a downloadable on-device LLM.
 *
 * [ModelKind.VISION] models ship an [mmprojUrl] projector alongside the main
 * [ggufUrl] weights; [ModelKind.TEXT] models are weights only, and every
 * projector field is null for them.
 *
 * URLs are pinned to specific file names (rather than "latest") so they don't
 * drift when the upstream repo adds new quantizations.
 */
data class AiModel(
    val id: String,
    val kind: ModelKind,
    val tier: ModelTier,
    val displayName: String,
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
     * Derived as roughly **6x [approxSizeBytes]**, which is not a guess: llama.cpp
     * maps the weights and the projector, so resident use tracks the on-disk
     * size closely (SmolVLM2 2.2B, 1.59 GiB on disk, measured ~1.5 GiB RSS on
     * device). A 1.59 GiB model on a nominally 8 GB phone - 4.7x - exhausted
     * ZRAM swap and had the low-memory killer closing background apps, so 4.7x
     * is known-too-tight and 6x is the smallest honest step past it.
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
    /** Vision projector URL. Null for [ModelKind.TEXT]. */
    val mmprojUrl: String? = null,
    /** Vision projector file name. Null for [ModelKind.TEXT]. */
    val mmprojFileName: String? = null,
    /** Exact on-disk size of the projector file, in bytes. Null for [ModelKind.TEXT]. */
    val mmprojSizeBytes: Long? = null,
) {

    /**
     * Total bytes the download costs. Measured, not estimated - the user sees
     * this before committing to it on mobile data.
     */
    val approxSizeBytes: Long get() = ggufSizeBytes + (mmprojSizeBytes ?: 0L)

    /**
     * The weights' share of the download, for weighting progress across the
     * files. Ranges from 0.63 (SmolVLM 256M) to 1.0 (every text model), so a
     * single hardcoded split would misreport most of the catalog.
     */
    val ggufProgressWeight: Float get() = ggufSizeBytes.toFloat() / approxSizeBytes

    companion object {
        private const val GIB = 1024L * 1024L * 1024L

        /** Where the prompt goes inside a [promptFormat]. */
        const val PROMPT_PLACEHOLDER = "{prompt}"

        /**
         * SmolVLM's own turn shape. Verified on device: SmolVLM 256M returns a
         * blank chat template, as does every other model tried.
         */
        private const val SMOLVLM_PROMPT =
            "<|im_start|>User: $PROMPT_PLACEHOLDER<end_of_utterance>\nAssistant:"

        /**
         * ChatML with one user turn and an empty system turn, transcribed from
         * LFM2's own `chat_template.jinja` rather than from memory of "roughly
         * ChatML". It closes turns with `<|im_end|>`, which [CompletionCleaner]
         * already stops on.
         *
         * The system turn is left empty on purpose. A dictionary system prompt
         * ("You are a precise German-English dictionary…") was measured against
         * this and scored no better, so the engine needs no per-request system
         * message and the whole instruction stays in [LlmPrompts].
         */
        private const val CHAT_ML =
            "<|im_start|>system\n<|im_end|>\n" +
                "<|im_start|>user\n$PROMPT_PLACEHOLDER<|im_end|>\n" +
                "<|im_start|>assistant\n"

        /**
         * The text model, for translation hints. Deliberately *one*, for every
         * device, rather than a tier ladder.
         *
         * Five candidates were scored on device against the same eleven German
         * words, through the shipped prompt and parser:
         *
         * | model | Q4 size | correct | clean | load |
         * |---|---|---|---|---|
         * | EuroLLM 1.7B | 1045 MB | 11/11 | 11/11 | 5.7 s |
         * | **LFM2-700M** | **469 MB** | **11/11** | **10/11** | **1.6 s** |
         * | LFM2-350M | 229 MB | 10/11 | 9/11 | 0.9 s |
         * | Qwen3-0.6B | 397 MB | ~8/11 | good | 2.2 s |
         * | granite-4.0-h-350m | 223 MB | 1-2/5 | - | answers blank |
         *
         * LFM2-700M matches EuroLLM's accuracy at 45% of the size and a third of
         * the load, which is what makes a single entry possible: at 469 MB its
         * RAM bar is ~3 GiB rather than 6, so there is no device that needs a
         * smaller fallback and no bottom rung that answers badly. A ladder whose
         * lowest step is unusable is worse than no ladder.
         *
         * It is not perfect: roughly one word in eleven comes back as prose -
         * `The German word "der Zug" translates to "the train" in English.` The
         * target is always the last quoted string there, so it is recoverable,
         * but the parser is deliberately left minimal instead.
         *
         * **Licence note:** LFM2 is under the LFM Open License, not Apache-2.0
         * like the rest of the catalog. It permits commercial use below a
         * revenue threshold. Worth revisiting if Voclet is ever sold.
         */
        val TEXT: List<AiModel> = listOf(
            AiModel(
                id = "lfm2-700m",
                kind = ModelKind.TEXT,
                tier = ModelTier.LOW,
                displayName = "LFM2 700M",
                ggufUrl = "https://huggingface.co/LiquidAI/LFM2-700M-GGUF/resolve/main/LFM2-700M-Q4_K_M.gguf",
                ggufFileName = "LFM2-700M-Q4_K_M.gguf",
                ggufSizeBytes = 468_624_320L,     // 447 MiB
                minRamBytes = 3 * GIB,
                promptFormat = CHAT_ML,
            ),
        )

        /**
         * Vision models, for reading word pairs off a photo. One entry per
         * [ModelTier], all from the ggml-org HuggingFace org (Apache-2.0),
         * served via the stable `/resolve/main/<file>` download endpoint.
         *
         * Every file name and size here was verified against the live repos.
         * That check mattered: the previous HIGH entry pointed at
         * `gemma-3n-E4B-it-Q4_K_M.gguf` and an `mmproj-gemma-3n-*` projector,
         * and *neither exists* - that repo publishes only Q8_0 and f16 weights
         * and no projector at all, so the tier could never have downloaded, let
         * alone read an image.
         *
         * MID and HIGH are the same model at different quantizations and so
         * share one projector file.
         */
        val VISION: List<AiModel> = listOf(
            AiModel(
                id = "smolvlm-256m",
                kind = ModelKind.VISION,
                tier = ModelTier.LOW,
                displayName = "SmolVLM 256M",
                ggufUrl = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf",
                ggufFileName = "SmolVLM-256M-Instruct-Q8_0.gguf",
                mmprojUrl = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-256M-Instruct-Q8_0.gguf",
                mmprojFileName = "mmproj-SmolVLM-256M-Instruct-Q8_0.gguf",
                ggufSizeBytes = 175_054_528L,
                mmprojSizeBytes = 103_769_856L,   // 266 MiB total
                minRamBytes = 2 * GIB,
                promptFormat = SMOLVLM_PROMPT,
            ),
            AiModel(
                id = "smolvlm2-2.2b",
                kind = ModelKind.VISION,
                tier = ModelTier.MID,
                displayName = "SmolVLM2 2.2B",
                ggufUrl = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/SmolVLM2-2.2B-Instruct-Q4_K_M.gguf",
                ggufFileName = "SmolVLM2-2.2B-Instruct-Q4_K_M.gguf",
                mmprojUrl = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                mmprojFileName = "mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                ggufSizeBytes = 1_112_602_656L,
                mmprojSizeBytes = 592_523_200L,   // 1.59 GiB total
                minRamBytes = 10 * GIB,
                promptFormat = SMOLVLM_PROMPT,
            ),
            AiModel(
                id = "smolvlm2-2.2b-q8",
                kind = ModelKind.VISION,
                tier = ModelTier.HIGH,
                displayName = "SmolVLM2 2.2B (Q8)",
                ggufUrl = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                ggufFileName = "SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                mmprojUrl = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                mmprojFileName = "mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                ggufSizeBytes = 1_927_933_984L,
                mmprojSizeBytes = 592_523_200L,   // 2.35 GiB total, shared projector
                minRamBytes = 14 * GIB,
                promptFormat = SMOLVLM_PROMPT,
            ),
        )

        /** Every downloadable model, both kinds. Ids are unique across the two. */
        val ALL: List<AiModel> = TEXT + VISION

        fun byId(id: String): AiModel? = ALL.firstOrNull { it.id == id }

        fun forKind(kind: ModelKind): List<AiModel> =
            if (kind == ModelKind.TEXT) TEXT else VISION

        fun forTier(kind: ModelKind, tier: ModelTier): AiModel =
            forKind(kind).first { it.tier == tier }
    }
}
