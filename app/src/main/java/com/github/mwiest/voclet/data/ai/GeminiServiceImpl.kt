package com.github.mwiest.voclet.data.ai

import android.graphics.Bitmap
import com.github.mwiest.voclet.data.ai.models.ExtractedWordPair
import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion
import com.github.mwiest.voclet.data.ai.models.WordPairExtractionResult
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiServiceImpl @Inject constructor(
    private val ai: FirebaseAI
) : GeminiService {

    // Gemini 1.5 Flash model for fast, cost-effective inference
    private val model: GenerativeModel by lazy {
        ai.generativeModel("gemini-1.5-flash")
    }

    override suspend fun extractWordPairsFromImage(
        image: Bitmap,
        preferredLanguage1: String?,
        preferredLanguage2: String?
    ): Result<WordPairExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildImageExtractionPrompt(preferredLanguage1, preferredLanguage2)

            val response = model.generateContent(
                content {
                    image(image)
                    text(prompt)
                }
            )

            val responseText =
                response.text ?: throw GeminiException.ParseError("Empty response from API")

            // Parse JSON response
            parseWordPairExtractionResponse(responseText)

        } catch (e: GeminiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(GeminiException.NetworkError(e))
        }
    }

    override suspend fun suggestTranslation(
        word: String,
        fromLanguage: String,
        toLanguage: String
    ): Result<TranslationSuggestion> = withContext(Dispatchers.IO) {
        try {
            if (word.isBlank()) {
                throw GeminiException.InvalidInput("Word cannot be empty")
            }

            val prompt = """
                Translate the word "$word" from $fromLanguage to $toLanguage.

                Provide your response in JSON format:
                {
                  "primaryTranslation": "main translation",
                  "alternatives": ["alternative1", "alternative2"],
                  "contextualNotes": "optional usage notes"
                }
            """.trimIndent()

            val response = model.generateContent(prompt)
            val responseText = response.text ?: throw GeminiException.ParseError("Empty response")

            parseTranslationResponse(responseText)

        } catch (e: GeminiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(GeminiException.NetworkError(e))
        }
    }

    override suspend fun suggestAutoCompletions(
        partialInput: String,
        language: String,
        existingWords: List<String>
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (partialInput.length < 2) {
                return@withContext Result.success(emptyList())
            }

            val prompt = buildAutoCompletePrompt(partialInput, language, existingWords)

            val response = model.generateContent(prompt)
            val responseText = response.text ?: throw GeminiException.ParseError("Empty response")

            parseAutoCompleteResponse(responseText)

        } catch (e: GeminiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(GeminiException.NetworkError(e))
        }
    }

    // Helper methods for prompt engineering

    private fun buildImageExtractionPrompt(lang1: String?, lang2: String?): String {
        val languageHint = when {
            lang1 != null && lang2 != null -> "Expected languages: $lang1 and $lang2."
            else -> "Detect the languages automatically."
        }

        return """
            You are a vocabulary learning assistant. Analyze this image containing a vocabulary list.

            Extract all word pairs from the image. $languageHint

            Provide your response in JSON format:
            {
              "detectedLanguage1": "language code (e.g., 'en')",
              "detectedLanguage2": "language code (e.g., 'es')",
              "wordPairs": [
                {"word1": "hello", "word2": "hola", "confidence": 0.95},
                {"word1": "goodbye", "word2": "adiós", "confidence": 0.90}
              ],
              "confidence": 0.92
            }

            Rules:
            - Only extract clear word pairs (word-to-word or phrase-to-phrase)
            - Ignore headers, titles, or unrelated text
            - Confidence should be between 0.0 and 1.0
            - Return empty wordPairs array if no valid pairs found
        """.trimIndent()
    }

    private fun buildAutoCompletePrompt(
        partial: String,
        language: String,
        existing: List<String>
    ): String {
        val existingContext = if (existing.isNotEmpty()) {
            "Existing words in vocabulary: ${existing.joinToString(", ")}"
        } else {
            ""
        }

        return """
            Suggest up to 5 word completions for "$partial" in $language.
            $existingContext

            Provide response as JSON array: ["completion1", "completion2", ...]

            Rules:
            - Only suggest common, appropriate words
            - Prioritize words that fit vocabulary learning context
            - Avoid duplicates with existing words
        """.trimIndent()
    }

    // Helper methods for parsing responses

    private fun parseWordPairExtractionResponse(json: String): Result<WordPairExtractionResult> {
        return try {
            val cleanJson = extractJsonFromResponse(json)
            val obj = JSONObject(cleanJson)

            val lang1 = obj.getString("detectedLanguage1")
            val lang2 = obj.getString("detectedLanguage2")
            val confidence = obj.optDouble("confidence", 1.0).toFloat()

            val pairsArray = obj.getJSONArray("wordPairs")
            val pairs = (0 until pairsArray.length()).map { i ->
                val pairObj = pairsArray.getJSONObject(i)
                ExtractedWordPair(
                    word1 = pairObj.getString("word1"),
                    word2 = pairObj.getString("word2"),
                    confidence = pairObj.optDouble("confidence", 1.0).toFloat()
                )
            }

            Result.success(WordPairExtractionResult(lang1, lang2, pairs, confidence))
        } catch (e: Exception) {
            Result.failure(GeminiException.ParseError("Failed to parse word pairs", e))
        }
    }

    private fun parseTranslationResponse(json: String): Result<TranslationSuggestion> {
        return try {
            val cleanJson = extractJsonFromResponse(json)
            val obj = JSONObject(cleanJson)

            val primary = obj.getString("primaryTranslation")
            val alternatives = obj.optJSONArray("alternatives")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            val notes = obj.optString("contextualNotes", null)

            Result.success(TranslationSuggestion(primary, alternatives, notes))
        } catch (e: Exception) {
            Result.failure(GeminiException.ParseError("Failed to parse translation", e))
        }
    }

    private fun parseAutoCompleteResponse(json: String): Result<List<String>> {
        return try {
            val cleanJson = extractJsonFromResponse(json)
            val array = JSONArray(cleanJson)
            val suggestions = (0 until array.length()).map { array.getString(it) }
            Result.success(suggestions.take(5))  // Max 5 suggestions
        } catch (e: Exception) {
            Result.failure(GeminiException.ParseError("Failed to parse auto-complete", e))
        }
    }

    /**
     * Extract JSON from response that may contain markdown code blocks.
     * Handles cases like: ```json\n{...}\n```
     */
    private fun extractJsonFromResponse(response: String): String {
        val trimmed = response.trim()

        // Check if wrapped in markdown code block
        return if (trimmed.startsWith("```")) {
            val lines = trimmed.lines()
            val jsonLines = lines.drop(1).dropLast(1)  // Remove first and last line
            jsonLines.joinToString("\n")
        } else {
            trimmed
        }
    }
}
