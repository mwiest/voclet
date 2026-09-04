package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmPromptsTest {

    @Test
    fun `translation names the languages in english, not by code`() {
        val prompt = LlmPrompts.translation("Haus", "de", "en")

        assertTrue(prompt.contains("German"))
        assertTrue(prompt.contains("English"))
        // The regression this guards: "Translate from de to en" made SmolVLM2
        // echo the German word back instead of translating it.
        assertFalse(prompt.contains(" de "))
        assertFalse(prompt.contains(" en "))
    }

    @Test
    fun `translation ends with a slot for the answer`() {
        val prompt = LlmPrompts.translation("Haus", "de", "en")

        assertTrue(prompt.contains("German: Haus"))
        assertTrue("the answer needs an explicit slot", prompt.trimEnd().endsWith("English:"))
    }

    @Test
    fun `translation carries the word verbatim`() {
        val prompt = LlmPrompts.translation("Fußgängerübergang", "de", "en")
        assertTrue(prompt.contains("Fußgängerübergang"))
    }

    @Test
    fun `image extraction names both languages in english`() {
        val prompt = LlmPrompts.imageExtraction("de", "fr")

        assertTrue(prompt.contains("German"))
        assertTrue(prompt.contains("French"))
    }

    @Test
    fun `image extraction stays usable with unknown languages`() {
        val prompt = LlmPrompts.imageExtraction(null, null)

        assertTrue(prompt.contains("word1"))
        assertTrue(prompt.contains("word2"))
        assertFalse("a null language must not leak into the prompt", prompt.contains("null"))
    }
}
