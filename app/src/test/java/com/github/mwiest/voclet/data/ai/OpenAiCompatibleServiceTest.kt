package com.github.mwiest.voclet.data.ai

import com.github.mwiest.voclet.data.database.AppSettings
import com.github.mwiest.voclet.data.database.AppSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end over a local socket: stored settings -> HTTP request -> parsed
 * domain model. Only the translation path is exercised here; the camera path
 * needs a real `Bitmap`, so its request shape is covered by
 * [com.github.mwiest.voclet.data.ai.cloud.ChatCompletionsTest] instead.
 */
class OpenAiCompatibleServiceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends the key, model and prompt to chat completions`() = runBlocking {
        server.enqueue(chatResponse("{\"primaryTranslation\":\"hola\"}"))

        serviceFor(configuredSettings(model = "my-vision-model"))
            .suggestTranslation("hello", "en", "es")
            .getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer sk-secret", request.getHeader("Authorization"))

        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("my-vision-model", body["model"]!!.jsonPrimitive.content)

        val prompt = body["messages"]!!.jsonArray.single()
            .jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(prompt.contains("hello"))
        assertTrue(prompt.contains("en"))
        assertTrue(prompt.contains("es"))
    }

    @Test
    fun `parses fenced assistant json into a translation suggestion`() = runBlocking {
        val fenced = """
            ```json
            {"primaryTranslation":"hola","alternatives":["buenos días"],"contextualNotes":"Informal"}
            ```
        """.trimIndent()
        server.enqueue(chatResponse(fenced))

        val suggestion = serviceFor(configuredSettings())
            .suggestTranslation("hello", "en", "es")
            .getOrThrow()

        assertEquals("hola", suggestion.primaryTranslation)
        assertEquals(listOf("buenos días"), suggestion.alternatives)
        assertEquals("Informal", suggestion.contextualNotes)
    }

    @Test
    fun `an unconfigured backend reports invalid input without any request`() = runBlocking {
        val error = serviceFor(AppSettings())
            .suggestTranslation("hello", "en", "es")
            .exceptionOrNull()

        assertTrue("got $error", error is CloudAiException.InvalidInput)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no settings row at all is treated as unconfigured`() = runBlocking {
        val error = serviceFor(null).suggestTranslation("hello", "en", "es").exceptionOrNull()
        assertTrue("got $error", error is CloudAiException.InvalidInput)
    }

    @Test
    fun `a blank word is rejected before any request`() = runBlocking {
        val error = serviceFor(configuredSettings())
            .suggestTranslation("   ", "en", "es")
            .exceptionOrNull()

        assertTrue("got $error", error is CloudAiException.InvalidInput)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `http 429 maps to rate limit exceeded`() = runBlocking {
        server.enqueue(errorResponse(429, "slow down"))

        val error = serviceFor(configuredSettings())
            .suggestTranslation("hello", "en", "es")
            .exceptionOrNull()

        assertTrue("got $error", error is CloudAiException.RateLimitExceeded)
    }

    @Test
    fun `other http errors surface the provider message`() = runBlocking {
        server.enqueue(errorResponse(400, "API key not valid"))

        val error = serviceFor(configuredSettings())
            .suggestTranslation("hello", "en", "es")
            .exceptionOrNull()

        assertTrue("got $error", error is CloudAiException.ApiError)
        assertTrue(error!!.message!!.contains("API key not valid"))
    }

    @Test
    fun `an error body without a message falls back to the status code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"))

        val error = serviceFor(configuredSettings())
            .suggestTranslation("hello", "en", "es")
            .exceptionOrNull()

        assertTrue("got $error", error is CloudAiException.ApiError)
        assertTrue(error!!.message!!.contains("502"))
    }

    @Test
    fun `an empty choices array is a parse error`() = runBlocking {
        server.enqueue(jsonResponse(200, "{\"choices\":[]}"))

        val error = serviceFor(configuredSettings())
            .suggestTranslation("hello", "en", "es")
            .exceptionOrNull()

        assertTrue("got $error", error is CloudAiException.ParseError)
    }

    @Test
    fun `prose without json is a parse error`() = runBlocking {
        server.enqueue(chatResponse("I am not able to translate that."))

        val error = serviceFor(configuredSettings())
            .suggestTranslation("hello", "en", "es")
            .exceptionOrNull()

        assertTrue("got $error", error is CloudAiException.ParseError)
    }

    // --- helpers ---

    private class FakeAppSettingsDao(private val stored: AppSettings?) : AppSettingsDao {
        override fun getSettings(): Flow<AppSettings?> = flowOf(stored)
        override suspend fun insertOrUpdate(settings: AppSettings) = Unit
    }

    private fun serviceFor(settings: AppSettings?) =
        OpenAiCompatibleService(FakeAppSettingsDao(settings))

    /** Settings pointing at the mock server, with a key already pasted. */
    private fun configuredSettings(model: String = "test-model") = AppSettings(
        aiCloudProvider = CloudProvider.CUSTOM,
        aiCloudBaseUrl = server.url("/v1/").toString(),
        aiCloudApiKey = "sk-secret",
        aiCloudModel = model,
    )

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class Choice(val message: Message)

    @Serializable
    private data class Completion(val choices: List<Choice>)

    @Serializable
    private data class ErrorDetail(val message: String)

    @Serializable
    private data class ErrorEnvelope(val error: ErrorDetail)

    /** A 200 whose single choice carries [content] as the assistant message. */
    private fun chatResponse(content: String): MockResponse = jsonResponse(
        code = 200,
        body = Json.encodeToString(Completion(listOf(Choice(Message("assistant", content))))),
    )

    private fun errorResponse(code: Int, message: String): MockResponse = jsonResponse(
        code = code,
        body = Json.encodeToString(ErrorEnvelope(ErrorDetail(message))),
    )

    private fun jsonResponse(code: Int, body: String): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
