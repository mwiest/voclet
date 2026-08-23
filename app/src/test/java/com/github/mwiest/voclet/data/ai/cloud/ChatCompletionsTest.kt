package com.github.mwiest.voclet.data.ai.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun firstMessage(body: String) =
        json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray.single().jsonObject

    @Test
    fun `text request carries the model and a plain string content`() {
        val body = ChatCompletions.textRequest("gemini-2.5-flash", "Translate cat to Spanish")

        val root = json.parseToJsonElement(body).jsonObject
        assertEquals("gemini-2.5-flash", root["model"]!!.jsonPrimitive.content)

        val message = firstMessage(body)
        assertEquals("user", message["role"]!!.jsonPrimitive.content)
        assertEquals("Translate cat to Spanish", message["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `vision request sends text plus an inline jpeg data uri`() {
        val body = ChatCompletions.visionRequest("pixtral-12b-2409", "Extract pairs", "QUJD")

        assertEquals(
            "pixtral-12b-2409",
            json.parseToJsonElement(body).jsonObject["model"]!!.jsonPrimitive.content,
        )

        val parts = firstMessage(body)["content"]!!.jsonArray
        assertEquals(2, parts.size)

        val text = parts[0].jsonObject
        assertEquals("text", text["type"]!!.jsonPrimitive.content)
        assertEquals("Extract pairs", text["text"]!!.jsonPrimitive.content)

        val image = parts[1].jsonObject
        assertEquals("image_url", image["type"]!!.jsonPrimitive.content)
        assertEquals(
            "data:image/jpeg;base64,QUJD",
            image["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `a prompt with quotes and newlines stays valid json`() {
        val body = ChatCompletions.textRequest("m", "Translate \"cat\"\nto Spanish")
        assertEquals("Translate \"cat\"\nto Spanish", firstMessage(body)["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reads the assistant content of the first choice`() {
        val content = ChatCompletions.assistantContent(
            """{"choices":[{"message":{"role":"assistant","content":"hola"}}]}""",
        )
        assertEquals("hola", content)
    }

    @Test
    fun `unusable response bodies yield null content instead of throwing`() {
        assertNull(ChatCompletions.assistantContent("""{"choices":[]}"""))
        assertNull(ChatCompletions.assistantContent("""{"choices":[{"message":{}}]}"""))
        assertNull(ChatCompletions.assistantContent("""{"choices":[{"message":{"content":" "}}]}"""))
        assertNull(ChatCompletions.assistantContent("not json at all"))
        assertNull(ChatCompletions.assistantContent(""))
    }

    @Test
    fun `reads a provider error message`() {
        val message = ChatCompletions.errorMessage(
            """{"error":{"message":"API key not valid","code":400}}""",
        )
        assertEquals("API key not valid", message)
    }

    @Test
    fun `a body without an error object yields no message`() {
        assertNull(ChatCompletions.errorMessage("""{"choices":[]}"""))
        assertNull(ChatCompletions.errorMessage("<html>502 Bad Gateway</html>"))
    }

    @Test
    fun `unicode in the prompt survives the round trip`() {
        val body = ChatCompletions.textRequest("m", "Übersetze „Haus“ ins Französische")
        assertTrue(firstMessage(body)["content"]!!.jsonPrimitive.content.contains("Übersetze"))
    }
}
