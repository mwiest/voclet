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

    /**
     * A plain single-turn text request.
     *
     * @param maxTokens ceiling on the *completion*, so a chatty or
     *   reasoning-heavy model cannot run far past the JSON we asked for -
     *   latency scales with tokens produced. Null leaves it to the provider.
     */
    fun textRequest(model: String, prompt: String, maxTokens: Int? = null): String =
        buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    },
                )
            }
            maxTokens?.let { put("max_tokens", it) }
        }.toString()

    /**
     * A single-turn request carrying one image, using the multi-part `content`
     * array form with a `data:` URI (no separate upload step, so it works
     * against any compatible endpoint).
     *
     * @param maxTokens ceiling on the *completion*. Note this is unrelated to
     *   the image's resolution: the picture is priced as input tokens, while
     *   this bounds only the word-pair JSON that comes back.
     */
    fun visionRequest(
        model: String,
        prompt: String,
        base64Jpeg: String,
        maxTokens: Int? = null,
    ): String = buildJsonObject {
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
        maxTokens?.let { put("max_tokens", it) }
    }.toString()

    /**
     * Why generation stopped for the first choice, e.g. `stop` (finished) or
     * `length` (hit the token ceiling, so the JSON is cut off mid-structure and
     * will not parse). Logged on failure, since a truncated response otherwise
     * looks like an unexplained parse error.
     */
    fun finishReason(body: String): String? = runCatching {
        val choice = root(body)?.get("choices")?.jsonArray?.firstOrNull() ?: return null
        choice.jsonObject["finish_reason"]?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * The assistant text of the first choice, or null if the body has no usable
     * content (empty `choices`, missing `content`, or unparseable JSON).
     */
    fun assistantContent(body: String): String? = runCatching {
        val choice = root(body)?.get("choices")?.jsonArray?.firstOrNull() ?: return null
        choice.jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * OpenRouter-style `error.metadata`, rendered compactly for logs.
     *
     * When a gateway proxies an upstream failure its own `error.message` is
     * generic ("Provider returned error"); the metadata names the provider and
     * quotes what it actually said, which is what separates "your quota is
     * spent" from "this shared endpoint is saturated".
     */
    fun errorMetadata(body: String): String? = runCatching {
        val metadata = root(body)?.get("error")?.jsonObject?.get("metadata")?.jsonObject
            ?: return@runCatching null
        val provider = metadata["provider_name"]?.jsonPrimitive?.content
        val raw = metadata["raw"]?.let { element ->
            runCatching { element.jsonPrimitive.content }.getOrElse { element.toString() }
        }
        listOfNotNull(provider, raw).joinToString(": ")
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
