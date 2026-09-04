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
    /**
     * Exact on-disk size of the weights file, in bytes, as the HuggingFace API
     * reports it for the pinned file name.
     */
    val ggufSizeBytes: Long,

    /** Exact on-disk size of the projector file, in bytes. */
    val mmprojSizeBytes: Long,
    /**
     * Total device RAM (bytes) below which this model should not be recommended.
     *
     * Derived as roughly **6x [approxSizeBytes]**, which is not a guess: llama.cpp
     * maps the weights and the projector, so resident use tracks the on-disk
     * size closely (SmolVLM2 2.2B, 1.59 GiB on disk, measured ~1.5 GiB RSS on
     * device). A 1.59 GiB model on a nominally 8 GB phone — 4.7x — exhausted
     * ZRAM swap and had the low-memory killer closing background apps, so 4.7x
     * is known-too-tight and 6x is the smallest honest step past it.
     *
     * Compared against `ActivityManager.MemoryInfo.totalMem`, which reports
     * *usable* RAM: an 8 GB phone reports about 7.5 GiB and a 16 GB one about
     * 15 GiB, because the kernel keeps a slice. Thresholds sit below the round
     * marketing number for that reason.
     */
    val minRamBytes: Long,
) {

    /**
     * Total bytes the download costs. Measured, not estimated - the user sees
     * this before committing to it on mobile data.
     */
    val approxSizeBytes: Long get() = ggufSizeBytes + mmprojSizeBytes

    /**
     * The weights' share of the download, for weighting progress across the two
     * files. Ranges from 0.63 (LOW) to 0.77 (HIGH), so a single hardcoded split
     * would misreport every model.
     */
    val ggufProgressWeight: Float get() = ggufSizeBytes.toFloat() / approxSizeBytes

    companion object {
        private const val GIB = 1024L * 1024L * 1024L

        /**
         * The model catalog, one entry per [ModelTier]. All weights come from
         * the ggml-org HuggingFace org (Apache-2.0), served via the stable
         * `/resolve/main/<file>` download endpoint.
         *
         * Every file name and size here was verified against the live repos.
         * That check mattered: the previous HIGH entry pointed at
         * `gemma-3n-E4B-it-Q4_K_M.gguf` and an `mmproj-gemma-3n-*` projector,
         * and *neither exists* — that repo publishes only Q8_0 and f16 weights
         * and no projector at all, so the tier could never have downloaded, let
         * alone read an image.
         *
         * MID and HIGH are the same model at different quantizations and so
         * share one projector file, which is why [ModelDownloader] must not
         * assume projector names are unique across the catalog.
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
                ggufSizeBytes = 175_054_528L,
                mmprojSizeBytes = 103_769_856L,   // 266 MiB total
                minRamBytes = 2 * GIB,
            ),
            AiModel(
                id = "smolvlm2-2.2b",
                tier = ModelTier.MID,
                displayName = "SmolVLM2 2.2B",
                ggufUrl = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/SmolVLM2-2.2B-Instruct-Q4_K_M.gguf",
                ggufFileName = "SmolVLM2-2.2B-Instruct-Q4_K_M.gguf",
                mmprojUrl = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                mmprojFileName = "mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                ggufSizeBytes = 1_112_602_656L,
                mmprojSizeBytes = 592_523_200L,   // 1.59 GiB total
                minRamBytes = 10 * GIB,
            ),
            AiModel(
                id = "smolvlm2-2.2b-q8",
                tier = ModelTier.HIGH,
                displayName = "SmolVLM2 2.2B (Q8)",
                ggufUrl = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                ggufFileName = "SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                mmprojUrl = "https://huggingface.co/ggml-org/SmolVLM2-2.2B-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                mmprojFileName = "mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
                ggufSizeBytes = 1_927_933_984L,
                mmprojSizeBytes = 592_523_200L,   // 2.35 GiB total, shared projector
                minRamBytes = 14 * GIB,
            ),
        )

        fun byId(id: String): AiModel? = ALL.firstOrNull { it.id == id }

        fun forTier(tier: ModelTier): AiModel = ALL.first { it.tier == tier }
    }
}
