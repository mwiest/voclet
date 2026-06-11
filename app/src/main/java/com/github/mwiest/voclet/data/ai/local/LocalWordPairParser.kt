package com.github.mwiest.voclet.data.ai.local

import com.github.mwiest.voclet.data.ai.models.ExtractedWordPair
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses a local model's word-pair extraction output into [ExtractedWordPair]s.
 *
 * We prompt for a bare JSON array of {"word1","word2"} objects, but small models
 * often wrap it in prose or markdown fences, so we extract the outermost
 * `[ ... ]` first and parse leniently. Malformed output yields an empty list
 * (callers treat that as "extraction failed" and fall back to manual entry).
 */
object LocalWordPairParser {

    @Serializable
    private data class RawPair(val word1: String = "", val word2: String = "")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): List<ExtractedWordPair> {
        val array = extractJsonArray(raw) ?: return emptyList()
        return try {
            json.decodeFromString<List<RawPair>>(array)
                .map { ExtractedWordPair(it.word1.trim(), it.word2.trim()) }
                .filter { it.word1.isNotEmpty() && it.word2.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Returns the substring from the first '[' to the last ']', or null if absent. */
    private fun extractJsonArray(raw: String): String? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }
}
