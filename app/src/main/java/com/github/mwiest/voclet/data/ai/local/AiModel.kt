package com.github.mwiest.voclet.data.ai.local

/**
 * Hardware tier a model targets. Used both to label models and to derive the
 * device's suggested tier from available RAM (see [DeviceHardware]).
 */
enum class ModelTier { LOW, MID, HIGH }

/**
 * Metadata describing a downloadable on-device LLM (GGUF + vision projector).
 *
 * One model handles both supported features (text translation hints + image
 * word-pair extraction), so every model ships a [mmprojUrl] vision projector
 * alongside the main [ggufUrl] weights.
 *
 * URLs are pinned to specific file names (rather than "latest") so they don't
 * drift when the upstream repo adds new quantizations.
 */
data class AiModel(
    val id: String,
    val tier: ModelTier,
    val displayName: String,
    val ggufUrl: String,
    val ggufFileName: String,
    val mmprojUrl: String,
    val mmprojFileName: String,
    /** Approximate combined on-disk size of GGUF + mmproj, in bytes. */
    val approxSizeBytes: Long,
    /** Minimum device RAM (bytes) at which this tier is recommended. */
    val minRamBytes: Long,
) {
    companion object {
        private const val GIB = 1024L * 1024L * 1024L

        /**
         * The model catalog, one entry per [ModelTier]. All weights come from
         * the ggml-org HuggingFace org (Apache-2.0), served via the stable
         * `/resolve/main/<file>` download endpoint.
         */
        val ALL: List<AiModel> = listOf(
            AiModel(
                id = "smolvlm-256m",
                tier = ModelTier.LOW,
                displayName = "SmolVLM 256M",
                ggufUrl = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf",
                ggufFileName = "SmolVLM-256M-Instruct-Q8_0.gguf",
                mmprojUrl = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-256M-Instruct-Q8_0.gguf",
                mmprojFileName = "mmproj-SmolVLM-256M-Instruct-Q8_0.gguf",
                approxSizeBytes = 280L * 1024 * 1024,
                minRamBytes = 0L,
            ),
            AiModel(
                id = "smolvlm2-2.2b",
                tier = ModelTier.MID,
                displayName = "SmolVLM2 2.2B",
                ggufUrl = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/SmolVLM2-2.2B-Instruct-Q4_K_M.gguf",
                ggufFileName = "SmolVLM2-2.2B-Instruct-Q4_K_M.gguf",
                mmprojUrl = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                mmprojFileName = "mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                approxSizeBytes = 1200L * 1024 * 1024,
                minRamBytes = 6 * GIB,
            ),
            // NOTE: the "high" tier file names below follow ggml-org's naming
            // convention but should be verified against the live repo before
            // release — the exact Gemma multimodal GGUF/mmproj names may differ.
            AiModel(
                id = "gemma-3n-e4b",
                tier = ModelTier.HIGH,
                displayName = "Gemma 3n E4B",
                ggufUrl = "https://huggingface.co/ggml-org/gemma-3n-E4B-it-GGUF/resolve/main/gemma-3n-E4B-it-Q4_K_M.gguf",
                ggufFileName = "gemma-3n-E4B-it-Q4_K_M.gguf",
                mmprojUrl = "https://huggingface.co/ggml-org/gemma-3n-E4B-it-GGUF/resolve/main/mmproj-gemma-3n-E4B-it-Q8_0.gguf",
                mmprojFileName = "mmproj-gemma-3n-E4B-it-Q8_0.gguf",
                approxSizeBytes = 5500L * 1024 * 1024,
                minRamBytes = 12 * GIB,
            ),
        )

        fun byId(id: String): AiModel? = ALL.firstOrNull { it.id == id }

        fun forTier(tier: ModelTier): AiModel = ALL.first { it.tier == tier }
    }
}
