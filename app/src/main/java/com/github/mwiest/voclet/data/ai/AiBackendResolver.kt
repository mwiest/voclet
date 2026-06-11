package com.github.mwiest.voclet.data.ai

/** The backend actually selected for a request after applying preference + availability. */
enum class ResolvedBackend { CLOUD, LOCAL }

/**
 * Pure decision logic mapping the user's [AiBackend] preference plus whether a
 * local model is available to the backend that should actually serve a request
 * (or `null` when no backend can — e.g. LOCAL preferred but nothing downloaded).
 */
object AiBackendResolver {
    fun resolve(preference: AiBackend, localModelAvailable: Boolean): ResolvedBackend? =
        when (preference) {
            AiBackend.CLOUD -> ResolvedBackend.CLOUD
            AiBackend.LOCAL -> if (localModelAvailable) ResolvedBackend.LOCAL else null
            AiBackend.AUTO -> if (localModelAvailable) ResolvedBackend.LOCAL else ResolvedBackend.CLOUD
        }
}
