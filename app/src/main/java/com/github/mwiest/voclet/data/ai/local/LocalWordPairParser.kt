package com.github.mwiest.voclet.data.ai.local

import com.github.mwiest.voclet.data.ai.models.ExtractedWordPair
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses a local model's word-pair extraction output into [ExtractedWordPair]s.
 *
 * We prompt for a bare JSON array of {"word1","word2"} objects, but small models
 * often wrap it in prose or markdown fences, so we extract the outermost
 * `[ ... ]` first and parse leniently. Malformed output yields an empty list
 * (callers treat that as "extraction failed" and fall back to manual entry).
 *
 * Three shapes come back for that one request and each is a page read
 * correctly: objects, two-element arrays and one flat alternating list.
 * Accepting only the first threw the other two away as extraction failures.
 */
object LocalWordPairParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): List<ExtractedWordPair> {
        val array = extractJsonArray(raw) ?: return emptyList()
        val elements = try {
            json.parseToJsonElement(array) as? JsonArray ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        return objectPairs(elements)
            ?: arrayPairs(elements)
            ?: flatPairs(elements)
            ?: emptyList()
    }

    /** `[{"word1":"das Haus","word2":"the house"}, ...]` — the shape we ask for. */
    private fun objectPairs(elements: List<JsonElement>): List<ExtractedWordPair>? =
        elements.filterIsInstance<JsonObject>()
            .mapNotNull { pairOf(it["word1"], it["word2"]) }
            .takeIf { it.isNotEmpty() }

    /** `[["das Haus","the house"], ...]` — how InternVL3 answers. */
    private fun arrayPairs(elements: List<JsonElement>): List<ExtractedWordPair>? =
        elements.filterIsInstance<JsonArray>()
            .filter { it.size == 2 }
            .mapNotNull { pairOf(it[0], it[1]) }
            .takeIf { it.isNotEmpty() }

    /**
     * `["das Haus","the house","laufen","to run", ...]` — how MiniCPM-V answers.
     *
     * Accepted only for an even-length list of nothing but non-empty strings:
     * anything else has lost a word somewhere, and pairing up regardless would
     * shift every row after the gap.
     */
    private fun flatPairs(elements: List<JsonElement>): List<ExtractedWordPair>? {
        if (elements.size < 2 || elements.size % 2 != 0) return null
        val words = elements.map { element ->
            val primitive = element as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            primitive.content.trim().ifEmpty { return null }
        }
        return words.chunked(2).map { ExtractedWordPair(it[0], it[1]) }
    }

    private fun pairOf(first: JsonElement?, second: JsonElement?): ExtractedWordPair? {
        val word1 = text(first) ?: return null
        val word2 = text(second) ?: return null
        return ExtractedWordPair(word1, word2)
    }

    private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)
        ?.takeIf { it !is JsonNull }
        ?.content?.trim()?.takeIf { it.isNotEmpty() }

    /** Returns the substring from the first '[' to the last ']', or null if absent. */
    private fun extractJsonArray(raw: String): String? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }
}
