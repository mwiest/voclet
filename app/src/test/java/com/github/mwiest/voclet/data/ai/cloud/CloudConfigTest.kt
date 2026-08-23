package com.github.mwiest.voclet.data.ai.cloud

import com.github.mwiest.voclet.data.ai.CloudProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudConfigTest {

    @Test
    fun `blank base url and model fall back to the preset`() {
        val config = resolveCloudConfig(
            provider = CloudProvider.GEMINI,
            baseUrl = "",
            apiKey = "sk-test",
            model = "",
        ).getOrThrow()

        assertEquals(CloudProvider.GEMINI.defaultBaseUrl, config.baseUrl)
        assertEquals(CloudProvider.GEMINI.defaultModel, config.model)
    }

    @Test
    fun `stored values override the preset`() {
        val config = resolveCloudConfig(
            provider = CloudProvider.GEMINI,
            baseUrl = "https://example.test/v1/",
            apiKey = "sk-test",
            model = "my-model",
        ).getOrThrow()

        assertEquals("https://example.test/v1/", config.baseUrl)
        assertEquals("my-model", config.model)
    }

    @Test
    fun `missing trailing slash is added so the path can be appended`() {
        val config = resolveCloudConfig(
            provider = CloudProvider.CUSTOM,
            baseUrl = "http://localhost:11434/v1",
            apiKey = "ollama",
            model = "llava",
        ).getOrThrow()

        assertEquals("http://localhost:11434/v1/", config.baseUrl)
        assertEquals("http://localhost:11434/v1/chat/completions", config.chatCompletionsUrl)
    }

    @Test
    fun `a blank api key is rejected`() {
        val error = resolveCloudConfig(CloudProvider.GEMINI, "", "   ", "").exceptionOrNull()
        assertEquals(CloudConfigError.MISSING_API_KEY, (error as CloudConfigException).error)
    }

    @Test
    fun `custom provider without a base url is rejected`() {
        val error = resolveCloudConfig(CloudProvider.CUSTOM, "", "key", "m").exceptionOrNull()
        assertEquals(CloudConfigError.MISSING_BASE_URL, (error as CloudConfigException).error)
    }

    @Test
    fun `custom provider without a model is rejected`() {
        val error = resolveCloudConfig(
            provider = CloudProvider.CUSTOM,
            baseUrl = "https://example.test/v1/",
            apiKey = "key",
            model = "",
        ).exceptionOrNull()
        assertEquals(CloudConfigError.MISSING_MODEL, (error as CloudConfigException).error)
    }

    @Test
    fun `every non-custom preset is usable with only an api key`() {
        CloudProvider.entries.filter { it != CloudProvider.CUSTOM }.forEach { provider ->
            val result = resolveCloudConfig(provider, "", "key", "")
            assertTrue("$provider should resolve", result.isSuccess)
            assertTrue(
                "$provider base url must end in /",
                result.getOrThrow().baseUrl.endsWith("/"),
            )
        }
    }
}
