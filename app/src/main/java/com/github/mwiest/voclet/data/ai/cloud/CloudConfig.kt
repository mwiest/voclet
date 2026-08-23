package com.github.mwiest.voclet.data.ai.cloud

import com.github.mwiest.voclet.data.ai.CloudProvider

/**
 * The three values a cloud request needs, after filling blanks from the
 * selected provider's preset.
 *
 * @property baseUrl Always ends in a single `/`, so `chat/completions` can be
 *   appended directly.
 */
data class CloudConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    val chatCompletionsUrl: String get() = baseUrl + "chat/completions"
}

/** Why a stored configuration cannot be used for a request. */
enum class CloudConfigError {
    /** No API key pasted yet — the user has not set cloud AI up at all. */
    MISSING_API_KEY,

    /** CUSTOM provider without a base URL, so we have nowhere to send the request. */
    MISSING_BASE_URL,

    /** CUSTOM provider without a model, so we cannot name one in the request. */
    MISSING_MODEL,
}

/**
 * Resolves stored settings into a usable [CloudConfig], falling back to the
 * provider preset wherever the user left a field blank.
 *
 * Pure so it can be unit-tested: only the presets and the stored strings matter.
 */
fun resolveCloudConfig(
    provider: CloudProvider,
    baseUrl: String,
    apiKey: String,
    model: String,
): Result<CloudConfig> {
    val key = apiKey.trim()
    if (key.isEmpty()) return Result.failure(CloudConfigException(CloudConfigError.MISSING_API_KEY))

    val url = baseUrl.trim().ifEmpty { provider.defaultBaseUrl }
    if (url.isEmpty()) return Result.failure(CloudConfigException(CloudConfigError.MISSING_BASE_URL))

    val modelId = model.trim().ifEmpty { provider.defaultModel }
    if (modelId.isEmpty()) return Result.failure(CloudConfigException(CloudConfigError.MISSING_MODEL))

    return Result.success(
        CloudConfig(
            baseUrl = if (url.endsWith("/")) url else "$url/",
            apiKey = key,
            model = modelId,
        ),
    )
}

/** Internal carrier for a [CloudConfigError]; the service maps it to a `GeminiException`. */
class CloudConfigException(val error: CloudConfigError) : Exception(error.name)
