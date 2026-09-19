package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmPromptsTest {

    @Test
    fun `translation names the languages in english, not by code`() {
        val prompt = LlmPrompts.translation("Haus", "de", "en")

        assertTrue(prompt.system.contains("German"))
        assertTrue(prompt.system.contains("English"))
        // The regression this guards: "Translate from de to en" made SmolVLM2
        // echo the German word back instead of translating it.
        assertFalse(prompt.system.contains(" de "))
        assertFalse(prompt.system.contains(" en "))
    }

    @Test
    fun `the instruction is in the system turn and the word alone in the user turn`() {
        // Measured, not stylistic: the same words in the user turn lose over
        // half the article accuracy.
        val prompt = LlmPrompts.translation("Haus", "de", "en")

        assertEquals("Haus", prompt.user)
        assertTrue("the instruction belongs in the system turn", prompt.system.isNotBlank())
    }

    @Test
    fun `translation asks for all three output rules`() {
        // Asking for only some of them scores worse than asking for all three.
        val system = LlmPrompts.translation("Haus", "de", "en").system.lowercase()

        assertTrue("lower case", system.contains("lower case"))
        assertTrue("infinitive", system.contains("infinitive"))
        assertTrue("article", system.contains("article"))
    }

    @Test
    fun `the instruction stays on one line`() {
        // Splitting it made the model answer with the second line verbatim.
        val system = LlmPrompts.translation("Haus", "de", "en").system
        assertFalse("a newline invites the model to continue the document", system.contains("\n"))
    }

    @Test
    fun `translation carries the word verbatim`() {
        val prompt = LlmPrompts.translation("Fußgängerübergang", "de", "en")
        assertEquals("Fußgängerübergang", prompt.user)
    }
}
