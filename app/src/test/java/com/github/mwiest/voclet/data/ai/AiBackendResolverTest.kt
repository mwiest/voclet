package com.github.mwiest.voclet.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiBackendResolverTest {

    @Test
    fun `cloud wins when configured and online`() {
        assertEquals(
            AiRouting.Use(ResolvedBackend.CLOUD),
            AiBackendResolver.resolve(
                cloudConfigured = true,
                online = true,
                localModelAvailable = true,
            ),
        )
    }

    @Test
    fun `falls back to the local model when offline`() {
        assertEquals(
            AiRouting.Use(ResolvedBackend.LOCAL),
            AiBackendResolver.resolve(
                cloudConfigured = true,
                online = false,
                localModelAvailable = true,
            ),
        )
    }

    @Test
    fun `uses the local model when cloud is not set up`() {
        assertEquals(
            AiRouting.Use(ResolvedBackend.LOCAL),
            AiBackendResolver.resolve(
                cloudConfigured = false,
                online = true,
                localModelAvailable = true,
            ),
        )
    }

    @Test
    fun `reports offline when cloud is the only backend and there is no network`() {
        assertEquals(
            AiRouting.Unavailable(AiUnavailableReason.OFFLINE),
            AiBackendResolver.resolve(
                cloudConfigured = true,
                online = false,
                localModelAvailable = false,
            ),
        )
    }

    @Test
    fun `reports not configured on a fresh install`() {
        assertEquals(
            AiRouting.Unavailable(AiUnavailableReason.NOT_CONFIGURED),
            AiBackendResolver.resolve(
                cloudConfigured = false,
                online = true,
                localModelAvailable = false,
            ),
        )
    }

    @Test
    fun `reports not configured when offline with nothing set up`() {
        assertEquals(
            AiRouting.Unavailable(AiUnavailableReason.NOT_CONFIGURED),
            AiBackendResolver.resolve(
                cloudConfigured = false,
                online = false,
                localModelAvailable = false,
            ),
        )
    }
}
