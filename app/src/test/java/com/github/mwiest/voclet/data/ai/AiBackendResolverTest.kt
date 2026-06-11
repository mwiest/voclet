package com.github.mwiest.voclet.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiBackendResolverTest {

    @Test
    fun `CLOUD preference always resolves to cloud`() {
        assertEquals(ResolvedBackend.CLOUD, AiBackendResolver.resolve(AiBackend.CLOUD, localModelAvailable = true))
        assertEquals(ResolvedBackend.CLOUD, AiBackendResolver.resolve(AiBackend.CLOUD, localModelAvailable = false))
    }

    @Test
    fun `LOCAL preference uses local when available, otherwise none`() {
        assertEquals(ResolvedBackend.LOCAL, AiBackendResolver.resolve(AiBackend.LOCAL, localModelAvailable = true))
        assertNull(AiBackendResolver.resolve(AiBackend.LOCAL, localModelAvailable = false))
    }

    @Test
    fun `AUTO prefers local when available, else falls back to cloud`() {
        assertEquals(ResolvedBackend.LOCAL, AiBackendResolver.resolve(AiBackend.AUTO, localModelAvailable = true))
        assertEquals(ResolvedBackend.CLOUD, AiBackendResolver.resolve(AiBackend.AUTO, localModelAvailable = false))
    }
}
