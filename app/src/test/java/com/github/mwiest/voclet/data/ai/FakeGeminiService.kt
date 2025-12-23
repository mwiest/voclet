package com.github.mwiest.voclet.data.ai

import android.graphics.Bitmap
import com.github.mwiest.voclet.data.ai.models.ExtractedWordPair
import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion
import com.github.mwiest.voclet.data.ai.models.WordPairExtractionResult

/**
 * Fake implementation of GeminiService for testing.
 * Allows tests to run without making real API calls.
 */
class FakeGeminiService : GeminiService {

    var shouldFail = false
    var extractionResult: WordPairExtractionResult? = null
    var translationResult: TranslationSuggestion? = null
    var autoCompleteResults: List<String> = emptyList()

    override suspend fun extractWordPairsFromImage(
        image: Bitmap,
        preferredLanguage1: String?,
        preferredLanguage2: String?
    ): Result<WordPairExtractionResult> {
        if (shouldFail) {
            return Result.failure(GeminiException.NetworkError(Exception("Test failure")))
        }

        return Result.success(extractionResult ?: WordPairExtractionResult(
            detectedLanguage1 = "en",
            detectedLanguage2 = "es",
            wordPairs = listOf(
                ExtractedWordPair("hello", "hola", 0.95f),
                ExtractedWordPair("goodbye", "adiós", 0.90f)
            ),
            confidence = 0.92f
        ))
    }

    override suspend fun suggestTranslation(
        word: String,
        fromLanguage: String,
        toLanguage: String
    ): Result<TranslationSuggestion> {
        if (shouldFail) {
            return Result.failure(GeminiException.NetworkError(Exception("Test failure")))
        }

        return Result.success(translationResult ?: TranslationSuggestion(
            primaryTranslation = "hola",
            alternatives = listOf("buenos días", "buenas tardes"),
            contextualNotes = "Informal greeting"
        ))
    }

    override suspend fun suggestAutoCompletions(
        partialInput: String,
        language: String,
        existingWords: List<String>
    ): Result<List<String>> {
        if (shouldFail) {
            return Result.failure(GeminiException.NetworkError(Exception("Test failure")))
        }

        return Result.success(autoCompleteResults.ifEmpty {
            listOf("${partialInput}1", "${partialInput}2", "${partialInput}3")
        })
    }
}
