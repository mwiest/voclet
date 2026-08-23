package com.github.mwiest.voclet.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.github.mwiest.voclet.data.ai.cloud.ChatCompletions
import com.github.mwiest.voclet.data.ai.cloud.CloudConfig
import com.github.mwiest.voclet.data.ai.cloud.CloudConfigException
import com.github.mwiest.voclet.data.ai.cloud.CloudPrompts
import com.github.mwiest.voclet.data.ai.cloud.CloudResponseParser
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

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use Result.failure(httpError(response.code, body))
                }
                val content = ChatCompletions.assistantContent(body)
                    ?: return@use Result.failure(
                        CloudAiException.ParseError("Empty response from API"),
                    )
                Result.success(content)
            }
        } catch (e: IOException) {
            Result.failure(CloudAiException.NetworkError(e))
        }
    }

    private fun httpError(code: Int, body: String): CloudAiException {
        if (code == HTTP_TOO_MANY_REQUESTS) return CloudAiException.RateLimitExceeded()
        val detail = ChatCompletions.errorMessage(body) ?: "HTTP $code"
        return CloudAiException.ApiError(detail)
    }

    private fun encodeJpeg(image: Bitmap): String {
        val stream = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 60L
        const val JPEG_QUALITY = 85
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val JSON_MEDIA_TYPE = "application/json"
    }
}
