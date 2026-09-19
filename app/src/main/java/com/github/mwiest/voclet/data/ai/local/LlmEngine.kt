package com.github.mwiest.voclet.data.ai.local

import kotlinx.coroutines.flow.Flow

/**
 * Raised when on-device inference fails. [kind] lets callers distinguish the
 * cases worth telling the user apart - a model that will not load is permanent,
 * whereas a slow load or a slow answer is worth retrying.
 */
class LlmException(
    message: String,
    val kind: Kind = Kind.FAILED,
) : Exception(message) {

    enum class Kind {
        FAILED,
        TIMEOUT,

        /** The model would not load at all - re-download it. */
        LOAD_FAILED,

        /**
         * The model is still loading. Distinct from [TIMEOUT] because it is the
         * one failure a plain retry reliably fixes: the load continues in the
         * background, so the next attempt usually finds it ready.
         */
        LOADING,
    }
}

/**
 * On-device LLM inference for translation hints. The method streams the model's
 * response as an *accumulating* string (every emission is the full text so
 * far), completing when generation finishes. If no model is downloaded the
 * returned flow is empty (graceful no-op) — callers can treat that as "local AI
 * unavailable".
 *
 * Reading a photo is not here: it is done by PP-OCRv5, which is not a language
 * model.
 *
 * Every other failure — load error, timeout, native error — is an
 * [LlmException] thrown from the flow, so a caller that shows progress always
 * learns why it stopped.
 */
interface LlmEngine {

    /** True if a model is downloaded and ready to use. */
    fun isModelAvailable(): Boolean

    /** Streams translation suggestions for [word] from [fromLang] to [toLang]. */
    fun suggestTranslation(word: String, fromLang: String, toLang: String): Flow<String>

    /** Releases the loaded model (e.g. on memory pressure). Safe to call anytime. */
    fun shutdown()
}
