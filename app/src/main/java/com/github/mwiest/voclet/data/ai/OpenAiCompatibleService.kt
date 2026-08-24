package com.github.mwiest.voclet.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.github.mwiest.voclet.data.ai.cloud.ChatCompletions
import com.github.mwiest.voclet.data.ai.cloud.CloudConfig
import com.github.mwiest.voclet.data.ai.cloud.CloudConfigException
import com.github.mwiest.voclet.data.ai.cloud.CloudPrompts
import com.github.mwiest.voclet.data.ai.cloud.CloudResponseParser
import com.github.mwiest.voclet.data.ai.cloud.ImageScaling
import com.github.mwiest.voclet.data.ai.cloud.resolveCloudConfig
import com.github.mwiest.voclet.data.ai.models.TranslationSuggestion
import com.github.mwiest.voclet.data.ai.models.WordPairExtractionResult
import com.github.mwiest.voclet.data.database.AppSettings
import com.github.mwiest.voclet.data.database.AppSettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud AI over any OpenAI-compatible `chat/completions` endpoint, using the
 * API key the user pasted in Settings.
 *
 * Voclet ships no key and talks to no service of its own: one REST shape covers
 * Gemini-direct, Groq, OpenRouter, Mistral and self-hosted Ollama, so the user
 * picks the provider and keeps the account. Configuration is read fresh on every
 * call, so edits in Settings take effect without restarting anything.
 */
@Singleton
class OpenAiCompatibleService @Inject constructor(
    private val appSettingsDao: AppSettingsDao,
) : CloudAiService {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun extractWordPairsFromImage(
        image: Bitmap,
        preferredLanguage1: String?,
        preferredLanguage2: String?,
    ): Result<WordPairExtractionResult> = withContext(Dispatchers.IO) {
        val config = currentConfig().getOrElse { return@withContext Result.failure(it) }
        val body = ChatCompletions.visionRequest(
            model = config.model,
            prompt = CloudPrompts.imageExtraction(preferredLanguage1, preferredLanguage2),
            base64Jpeg = encodeJpeg(image),
            maxTokens = EXTRACTION_MAX_TOKENS,
        )
        complete(config, body).mapCatching { CloudResponseParser.parseWordPairs(it).getOrThrow() }
    }

    override suspend fun suggestTranslation(
        word: String,
        fromLanguage: String,
        toLanguage: String,
    ): Result<TranslationSuggestion> = withContext(Dispatchers.IO) {
        if (word.isBlank()) {
            return@withContext Result.failure(CloudAiException.InvalidInput("Word cannot be empty"))
        }
        val config = currentConfig().getOrElse { return@withContext Result.failure(it) }
        val body = ChatCompletions.textRequest(
            model = config.model,
            prompt = CloudPrompts.translation(word, fromLanguage, toLanguage),
            maxTokens = TRANSLATION_MAX_TOKENS,
        )
        complete(config, body).mapCatching { CloudResponseParser.parseTranslation(it).getOrThrow() }
    }

    /**
     * Reads the stored provider configuration, mapping an unconfigured cloud
     * backend to [CloudAiException.InvalidInput] — the same "AI unavailable"
     * shape callers already handle when no on-device model is downloaded.
     */
    private suspend fun currentConfig(): Result<CloudConfig> {
        val settings = appSettingsDao.getSettings().first() ?: AppSettings()
        return resolveCloudConfig(
            provider = settings.aiCloudProvider,
            baseUrl = settings.aiCloudBaseUrl,
            apiKey = settings.aiCloudApiKey,
            model = settings.aiCloudModel,
        ).recoverCatching { cause ->
            val reason = (cause as? CloudConfigException)?.error?.name ?: "not configured"
            Log.w(AI_LOG_TAG, "Cloud AI unusable: $reason (provider=${settings.aiCloudProvider})")
            throw CloudAiException.InvalidInput("Cloud AI is not configured ($reason)")
        }
    }

    /** POSTs one chat completion and returns the assistant's text. */
    private fun complete(config: CloudConfig, jsonBody: String): Result<String> {
        val request = Request.Builder()
            .url(config.chatCompletionsUrl)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", JSON_MEDIA_TYPE)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        Log.d(
            AI_LOG_TAG,
            "POST ${config.chatCompletionsUrl} model=${config.model} " +
                "key=${config.apiKey.length} chars, ${jsonBody.length} byte body",
        )
        val startedAt = System.currentTimeMillis()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val elapsed = System.currentTimeMillis() - startedAt
                if (!response.isSuccessful) {
                    val error = httpError(response.code, body)
                    Log.w(
                        AI_LOG_TAG,
                        "HTTP ${response.code} after ${elapsed}ms: ${error.message}",
                    )
                    ChatCompletions.errorMetadata(body)?.let {
                        Log.w(AI_LOG_TAG, "Upstream detail: $it")
                    }
                    if (ChatCompletions.errorMessage(body) == null) {
                        // Nothing quotable came out of the body, which leaves
                        // "HTTP <code>" and no idea why. Log the start of the raw
                        // body so the provider's actual shape is visible. Error
                        // responses carry diagnostics rather than the user's
                        // vocabulary, and the snippet is truncated so a provider
                        // that echoes the request cannot spill much of it.
                        val snippet = body.take(ERROR_BODY_SNIPPET_CHARS)
                            .replace(WHITESPACE_RUN, " ")
                            .trim()
                        Log.w(
                            AI_LOG_TAG,
                            "Unparsed error body: ${snippet.ifEmpty { "(empty)" }}",
                        )
                    }
                    if (response.code == HTTP_TOO_MANY_REQUESTS) {
                        // Which quota was hit, and when it resets. A shared free
                        // tier can 429 on someone else's traffic, so the headers
                        // are what separate "your quota" from "provider busy".
                        val quota = RATE_LIMIT_HEADERS
                            .mapNotNull { name -> response.header(name)?.let { "$name=$it" } }
                            .joinToString(" ")
                            .ifEmpty { "none sent by provider" }
                        Log.w(AI_LOG_TAG, "Rate-limit headers: $quota")
                    }
                    return@use Result.failure(error)
                }
                // "length" means the completion hit the ceiling, so the JSON is
                // cut off mid-structure and the parse failure that follows would
                // otherwise look inexplicable.
                val finishReason = ChatCompletions.finishReason(body)
                if (finishReason != null && finishReason != FINISH_REASON_STOP) {
                    Log.w(AI_LOG_TAG, "Generation stopped early: finish_reason=$finishReason")
                }

                val content = ChatCompletions.assistantContent(body)
                if (content == null) {
                    Log.w(AI_LOG_TAG, "HTTP 200 after ${elapsed}ms but no assistant content")
                    return@use Result.failure(
                        CloudAiException.ParseError("Empty response from API"),
                    )
                }
                Log.d(AI_LOG_TAG, "HTTP 200 after ${elapsed}ms, ${content.length} chars")
                Result.success(content)
            }
        } catch (e: IOException) {
            Log.w(AI_LOG_TAG, "Request to ${config.chatCompletionsUrl} failed", e)
            Result.failure(CloudAiException.NetworkError(e))
        }
    }

    private fun httpError(code: Int, body: String): CloudAiException {
        val detail = ChatCompletions.errorMessage(body)
        if (code == HTTP_TOO_MANY_REQUESTS) return CloudAiException.RateLimitExceeded(detail)
        return CloudAiException.ApiError(detail ?: "HTTP $code")
    }

    /**
     * Shrinks the capture to [MAX_IMAGE_LONG_EDGE_PX] if needed, then JPEG- and
     * Base64-encodes it for the `data:` URI.
     *
     * Scaling happens here rather than at capture time so the camera keeps its
     * full-resolution bitmap for anything else (the on-device model does its own
     * preprocessing), while every cloud request is bounded whatever it is fed.
     */
    private fun encodeJpeg(image: Bitmap): String {
        val target = ImageScaling.targetSize(image.width, image.height, MAX_IMAGE_LONG_EDGE_PX)
        val scaled = target
            ?.let { Bitmap.createScaledBitmap(image, it.width, it.height, true) }
            ?: image

        val sentSize = "${scaled.width}x${scaled.height}"
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        // Only ever recycle the copy made here; the original belongs to the caller.
        if (scaled !== image) scaled.recycle()

        val bytes = stream.toByteArray()
        Log.d(
            AI_LOG_TAG,
            "Image ${image.width}x${image.height} -> $sentSize, ${bytes.size / 1024} KB JPEG",
        )
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 60L
        const val JPEG_QUALITY = 85
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val JSON_MEDIA_TYPE = "application/json"

        /** The only finish_reason that means the model said everything it meant to. */
        const val FINISH_REASON_STOP = "stop"

        /** How much of an unparseable error body to log; enough to see its shape. */
        const val ERROR_BODY_SNIPPET_CHARS = 300

        /** Collapses the newlines in an HTML or pretty-printed error into one log line. */
        val WHITESPACE_RUN = Regex("\\s+")

        /**
         * Longest edge, in pixels, of a photo sent to a cloud model.
         *
         * Printed vocabulary lists stay legible well below the camera's native
         * resolution, and 1600px keeps a full page's text readable while cutting
         * a multi-megapixel capture to a few hundred KB - a payload that also
         * survives providers which cap image size. Raise it only if extraction
         * starts missing small print.
         */
        const val MAX_IMAGE_LONG_EDGE_PX = 1600

        /**
         * Completion ceiling for a translation suggestion.
         *
         * The answer is one small JSON object: a translation, a handful of
         * alternatives and a sentence of notes - about 120 tokens in practice.
         * The ceiling is deliberately several times that, so it never truncates
         * a legitimate answer (long compounds, non-Latin scripts that tokenize
         * badly, or a reasoning model whose thinking counts against the same
         * budget) while still stopping a rambling model from running for
         * thousands of tokens.
         */
        const val TRANSLATION_MAX_TOKENS = 512

        /**
         * Completion ceiling for camera import.
         *
         * Independent of camera resolution: the picture costs *input* tokens,
         * while this bounds only the JSON coming back. It scales with how many
         * word pairs are on the page:
         *
         *   ~40 tokens per pair ({"word1":…,"word2":…,"confidence":0.95},
         *   generously counted for accents and non-Latin scripts)
         *   x 100 pairs, more than a dense double page holds
         *   + ~60 tokens of envelope (title, both language codes, confidence)
         *   ~= 4060, rounded to 4096.
         *
         * Well under the smallest per-endpoint completion cap seen on the
         * providers Voclet talks to (16k).
         */
        const val EXTRACTION_MAX_TOKENS = 4096

        /** Logged verbatim on a 429; providers send whichever subset they use. */
        val RATE_LIMIT_HEADERS = listOf(
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
            "Retry-After",
        )
    }
}
