package com.github.mwiest.voclet.data.ai

/** The backend actually selected for a request. */
enum class ResolvedBackend { CLOUD, LOCAL }

/** Why no backend can serve a request. */
enum class AiUnavailableReason {
    /** Neither a cloud key nor an on-device model has been set up yet. */
    NOT_CONFIGURED,

    /** Cloud AI is set up but the device is offline, with no local model to fall back on. */
    OFFLINE,
}

/** Outcome of [AiBackendResolver.resolve]. */
sealed interface AiRouting {
    data class Use(val backend: ResolvedBackend) : AiRouting
    data class Unavailable(val reason: AiUnavailableReason) : AiRouting
}

/**
 * Picks the backend for a request from what is actually set up, with no user
 * preference involved.
 *
 * There is no backend toggle in Settings: the user configures cloud AI,
 * downloads an on-device model, or both, and Voclet routes on availability.
 * Cloud wins when it is usable — every cloud model is far stronger than a
 * model that fits on a tablet, and camera import in particular depends on it —
 * and the on-device model is what keeps the features working offline.
 *
 * Pure so it can be unit-tested: the three inputs are gathered by the caller.
 */
object AiBackendResolver {
    fun resolve(
        cloudConfigured: Boolean,
        online: Boolean,
        localModelAvailable: Boolean,
    ): AiRouting = when {
        cloudConfigured && online -> AiRouting.Use(ResolvedBackend.CLOUD)
        localModelAvailable -> AiRouting.Use(ResolvedBackend.LOCAL)
        cloudConfigured -> AiRouting.Unavailable(AiUnavailableReason.OFFLINE)
        else -> AiRouting.Unavailable(AiUnavailableReason.NOT_CONFIGURED)
    }
}
