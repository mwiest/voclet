package com.github.mwiest.voclet.data.ai.cloud

import com.github.mwiest.voclet.data.ai.GeminiException
import com.github.mwiest.voclet.data.ai.models.ExtractedWordPair
import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion
import com.github.mwiest.voclet.data.ai.models.WordPairExtractionResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Turns the JSON a cloud model writes (see [CloudPrompts]) into domain models.
 *
 * Models often wrap the object in prose or a markdown fence and sometimes emit
 * the literal string "null" for absent fields, so parsing is deliberately
 * lenient: we take the outermost `{ ... }` and tolerate missing keys.
 */
object CloudResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Serializable
    private data class RawExtraction(
        val title: String? = null,
        val detectedLanguage1: String = "",
        val detectedLanguage2: String = "",
        val wordPairs: List<RawPair> = emptyList(),
        val confidence: Float = 1.0f,
    )

    @Serializable
    private data class RawPair(
        val word1: String = "",
        val word2: String = "",
        val confidence: Float = 1.0f,
    )

    @Serializable
    private data class RawTranslation(
        @SerialName("primaryTranslation") val primary: String = "",
        val alternatives: List<String> = emptyList(),
        val contextualNotes: String? = null,
    )

    fun parseWordPairs(raw: String): Result<WordPairExtractionResult> {
        val body = extractJsonObject(raw)
            ?: return Result.failure(GeminiException.ParseError("No JSON object in response"))
        return try {
            val parsed = json.decodeFromString<RawExtraction>(body)
            Result.success(
                WordPairExtractionResult(
                    title = parsed.title?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
                    detectedLanguage1 = parsed.detectedLanguage1,
                    detectedLanguage2 = parsed.detectedLanguage2,
                    wordPairs = parsed.wordPairs
                        .map { ExtractedWordPair(it.word1.trim(), it.word2.trim(), it.confidence) }
                        .filter { it.word1.isNotEmpty() && it.word2.isNotEmpty() },
                    confidence = parsed.confidence,
                ),
            )
        } catch (e: Exception) {
            Result.failure(GeminiException.ParseError("Failed to parse word pairs", e))
        }
    }

    fun parseTranslation(raw: String): Result<TranslationSuggestion> {
        val body = extractJsonObject(raw)
            ?: return Result.failure(GeminiException.ParseError("No JSON object in response"))
        return try {
            val parsed = json.decodeFromString<RawTranslation>(body)
            val primary = parsed.primary.trim()
            if (primary.isEmpty()) {
                return Result.failure(GeminiException.ParseError("No translation in response"))
            }
            Result.success(
                TranslationSuggestion(
                    primaryTranslation = primary,
                    alternatives = parsed.alternatives.map { it.trim() }.filter { it.isNotEmpty() },
                    contextualNotes = parsed.contextualNotes
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() && it != "null" },
                ),
            )
        } catch (e: Exception) {
            Result.failure(GeminiException.ParseError("Failed to parse translation", e))
        }
    }

    /**
     * Returns the substring from the first `{` to the last `}`, which strips
     * markdown fences and surrounding chatter in one step. Null if absent.
     */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }
}
