package com.github.mwiest.voclet.data.ai

/**
 * Presets for OpenAI-compatible cloud endpoints the user can point Voclet at.
 *
 * Voclet ships no API key of its own: the user brings their own key from any
 * provider that speaks the OpenAI `chat/completions` protocol. These presets
 * only pre-fill base URL and model so the common cases need one paste instead
 * of three; both fields stay editable, and [CUSTOM] covers anything else
 * (self-hosted Ollama, a corporate gateway, …).
 *
 * Default model IDs drift as providers retire models. They are a starting
 * point, not a guarantee — the user can always correct the model in Settings.
 *
 * @property defaultBaseUrl OpenAI-compatible API root, with a trailing slash;
 *   `chat/completions` is appended to it.
 * @property defaultModel A vision-capable model, since camera import sends images.
 */
enum class CloudProvider(
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    GEMINI(
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        defaultModel = "gemini-2.5-flash",
    ),
    GROQ(
        defaultBaseUrl = "https://api.groq.com/openai/v1/",
        defaultModel = "meta-llama/llama-4-scout-17b-16e-instruct",
    ),
    OPENROUTER(
        defaultBaseUrl = "https://openrouter.ai/api/v1/",
        defaultModel = "meta-llama/llama-4-maverick:free",
    ),
    MISTRAL(
        defaultBaseUrl = "https://api.mistral.ai/v1/",
        defaultModel = "pixtral-12b-2409",
    ),
    CUSTOM(
        defaultBaseUrl = "",
        defaultModel = "",
    ),
}
