package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionCleanerTest {

    @Test
    fun `an ordinary answer is left alone`() {
        assertEquals("the animal", CompletionCleaner.clean("the animal"))
        assertEquals("post office", CompletionCleaner.clean("  post office  "))
    }

    @Test
    fun `the observed das Tier failure is cleaned away`() {
        // Verbatim from the device, 2026-09-05: the alternatives pass ran to its
        // token cap emitting the marker twice, and both fragments reached the
        // user as offered translations.
        val raw = "English:<end_of_utterance>\nEnglish:<end_of_"
        assertEquals("English:", CompletionCleaner.clean(raw))
    }

    @Test
    fun `everything from the first stop marker on is dropped`() {
        assertEquals("the animal", CompletionCleaner.clean("the animal<end_of_utterance>"))
        assertEquals("the animal", CompletionCleaner.clean("the animal<|im_end|>\nmore"))
        assertEquals("the animal", CompletionCleaner.clean("the animal<end_of_turn>x"))
        assertEquals("the animal", CompletionCleaner.clean("the animal<|endoftext|>"))
    }

    @Test
    fun `a hallucinated next turn is dropped`() {
        assertEquals("the animal", CompletionCleaner.clean("the animal\nUser: and now"))
    }

    @Test
    fun `a marker cut in half by the token cap is dropped`() {
        // The case no stop sequence can match, and the one that got through.
        assertEquals("the animal", CompletionCleaner.clean("the animal<end_of_"))
        assertEquals("the animal", CompletionCleaner.clean("the animal<"))
        assertEquals("the animal", CompletionCleaner.clean("the animal<|im_"))
    }

    @Test
    fun `a closed marker later in the text is not mistaken for a fragment`() {
        // The dangling-fragment rule must only fire on an *unclosed* '<'.
        assertEquals("a <b> c", CompletionCleaner.clean("a <b> c"))
    }

    @Test
    fun `output that is nothing but scaffolding cleans to empty`() {
        // The caller treats blank as "no output", which is the honest reading:
        // the model answered with turn markers and no words.
        assertEquals("", CompletionCleaner.clean("<end_of_utterance>"))
        assertEquals("", CompletionCleaner.clean("<end_of_"))
    }

    @Test
    fun `the stop list is what the engine hands native`() {
        // One list for both jobs. If these ever diverge, native stops on one set
        // of markers and the text is cleaned of another.
        assertEquals(
            CompletionCleaner.STOP_SEQUENCES,
            CompletionCleaner.STOP_SEQUENCES.distinct(),
        )
        assertEquals(true, CompletionCleaner.STOP_SEQUENCES.all { it.isNotBlank() })
    }
}
