package com.github.mwiest.voclet.data.ai

import android.graphics.Bitmap
import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion
import com.github.mwiest.voclet.data.ai.models.WordPairExtractionResult

/**
 * Cloud AI for vocabulary learning: extracting word pairs from a photo of a
 * vocabulary list, and suggesting a translation for a single word.
 *
 * Implemented by [OpenAiCompatibleService], which talks to whichever
 * OpenAI-compatible endpoint the user configured. The on-device counterpart is
 * [com.github.mwiest.voclet.data.ai.local.LlmEngine]; which one a caller gets
 * is decided by [AiBackendResolver].
 */
interface CloudAiService {

    /**
     * Extract word pairs from an image containing vocabulary lists.
     *
     * Uses the model's vision capabilities to:
     * 1. Detect and extract text (OCR)
     * 2. Identify language pairs
     * 3. Parse word pairs
     *
     * @param image Bitmap of the captured image (from camera)
     * @param preferredLanguage1 Optional hint for first language (e.g., "en")
     * @param preferredLanguage2 Optional hint for second language (e.g., "es")
     * @return WordPairExtractionResult containing detected pairs and languages
     * @throws CloudAiException if API call fails or response cannot be parsed
     */
    suspend fun extractWordPairsFromImage(
        image: Bitmap,
        preferredLanguage1: String? = null,
        preferredLanguage2: String? = null
    ): Result<WordPairExtractionResult>

    /**
     * Suggest a translation for a given word.
     *
     * @param word The word to translate
     * @param fromLanguage Source language code (e.g., "en")
     * @param toLanguage Target language code (e.g., "es")
     * @return TranslationSuggestion with primary translation and alternatives
     * @throws CloudAiException if API call fails
     */
    suspend fun suggestTranslation(
        word: String,
        fromLanguage: String,
        toLanguage: String
    ): Result<TranslationSuggestion>
}

/**
 * Exception thrown when a cloud AI request fails.
 */
sealed class CloudAiException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class NetworkError(cause: Throwable) : CloudAiException("Network error occurred", cause)
    class ApiError(message: String) : CloudAiException("API error: $message")
    class ParseError(message: String, cause: Throwable? = null) :
        CloudAiException("Failed to parse response: $message", cause)

    /**
     * HTTP 429. [detail] carries the provider's own explanation when it sends
     * one: on a shared free tier that is the only thing distinguishing "you hit
     * your daily quota" from "the upstream model is saturated right now", and
     * the two need opposite responses from the user.
     */
    class RateLimitExceeded(val detail: String? = null) : CloudAiException(
        if (detail != null) {
            "Rate limit exceeded: $detail"
        } else {
            "Rate limit exceeded. Please try again later."
        },
    )
    class InvalidInput(message: String) : CloudAiException("Invalid input: $message")
}
