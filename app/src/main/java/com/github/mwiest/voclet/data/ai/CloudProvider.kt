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
 * Where a provider offers a router or floating alias, prefer it over a pinned
 * model ID for exactly that reason.
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
        // Floating alias, hot-swapped by Google to the current Flash release, so it
        // does not need updating here. Flash accepts image input.
        defaultModel = "gemini-flash-latest",
    ),
    GROQ(
        defaultBaseUrl = "https://api.groq.com/openai/v1/",
        // Unverified: Groq publishes no capability column in its public model docs
        // and its /models endpoint needs a key, so this ID could not be checked
        // against a live list. Groq offers no router alias to fall back on.
        defaultModel = "meta-llama/llama-4-scout-17b-16e-instruct",
    ),
    OPENROUTER(
        defaultBaseUrl = "https://openrouter.ai/api/v1/",
        // A virtual router, not a fixed model: it picks a free model per request and
        // filters for the capabilities the request needs, image understanding
        // included. Unlike a pinned model ID it cannot go stale.
        defaultModel = "openrouter/free",
    ),
    MISTRAL(
        defaultBaseUrl = "https://api.mistral.ai/v1/",
        // Replaces pixtral-12b-2409, retired 2025-12-02. Mistral publishes no
        // "-latest" aliases, and this exact ID string is unverified against a live
        // list (the docs give versioned IDs only).
        defaultModel = "ministral-3-14b-25-12",
    ),
    CUSTOM(
        defaultBaseUrl = "",
        defaultModel = "",
    ),
}
