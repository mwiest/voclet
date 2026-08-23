package com.github.mwiest.voclet.data.ai.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Request bodies and response reading for the OpenAI `chat/completions`
 * protocol, which every provider preset in [com.github.mwiest.voclet.data.ai.CloudProvider]
 * speaks.
 *
 * Deliberately free of Android and HTTP types: bodies are built from a Base64
 * string rather than a `Bitmap`, so the wire format is unit-testable.
 */
object ChatCompletions {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** A plain single-turn text request. */
    fun textRequest(model: String, prompt: String): String = buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                },
            )
        }
    }.toString()

    /**
     * A single-turn request carrying one image, using the multi-part `content`
     * array form with a `data:` URI (no separate upload step, so it works
     * against any compatible endpoint).
     */
    fun visionRequest(model: String, prompt: String, base64Jpeg: String): String = buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "user")
                    put(
                        "content",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", prompt)
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:image/jpeg;base64,$base64Jpeg")
                                    }
                                },
                            )
                        },
                    )
                },
            )
        }
    }.toString()

    /**
     * The assistant text of the first choice, or null if the body has no usable
     * content (empty `choices`, missing `content`, or unparseable JSON).
     */
    fun assistantContent(body: String): String? = runCatching {
        val choice = root(body)?.get("choices")?.jsonArray?.firstOrNull() ?: return null
        choice.jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * The provider's own error text (`error.message`) if the body carries one.
     * Providers vary in how much they include, so callers fall back to the
     * HTTP status when this is null.
     */
    fun errorMessage(body: String): String? = runCatching {
        root(body)?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun root(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
}
