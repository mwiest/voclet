package com.github.mwiest.voclet.data.ai

import android.graphics.Bitmap
import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion
import com.github.mwiest.voclet.data.ai.models.WordPairExtractionResult

/**
 * Service interface for interacting with Firebase AI (Gemini).
 *
 * This service provides AI-powered features for vocabulary learning:
 * - Extract word pairs from images (camera OCR + translation)
 * - Suggest translations for words
 * - Generate auto-completion suggestions
 */
interface GeminiService {

    /**
     * Extract word pairs from an image containing vocabulary lists.
     *
     * Analyzes the image using Gemini's vision capabilities to:
     * 1. Detect and extract text (OCR)
     * 2. Identify language pairs
     * 3. Parse word pairs
     *
     * @param image Bitmap of the captured image (from camera)
     * @param preferredLanguage1 Optional hint for first language (e.g., "en")
     * @param preferredLanguage2 Optional hint for second language (e.g., "es")
     * @return WordPairExtractionResult containing detected pairs and languages
     * @throws GeminiException if API call fails or response cannot be parsed
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
     * @throws GeminiException if API call fails
     */
    suspend fun suggestTranslation(
        word: String,
        fromLanguage: String,
        toLanguage: String
    ): Result<TranslationSuggestion>
}

/**
 * Exception thrown when Gemini API interactions fail.
 */
sealed class GeminiException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class NetworkError(cause: Throwable) : GeminiException("Network error occurred", cause)
    class ApiError(message: String) : GeminiException("API error: $message")
    class ParseError(message: String, cause: Throwable? = null) :
        GeminiException("Failed to parse response: $message", cause)

    class RateLimitExceeded : GeminiException("Rate limit exceeded. Please try again later.")
    class InvalidInput(message: String) : GeminiException("Invalid input: $message")
}
