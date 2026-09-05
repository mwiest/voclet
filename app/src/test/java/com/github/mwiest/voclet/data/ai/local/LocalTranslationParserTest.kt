package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalTranslationParserTest {

    @Test
    fun `the pipe separator splits the translation from further meanings`() {
        // The shape the prompt asks for: best word first, the rest behind a pipe.
        val result = LocalTranslationParser.parse("animal | beast, creature")!!
        assertEquals("animal", result.primaryTranslation)
        assertEquals(listOf("beast", "creature"), result.alternatives)
    }

    @Test
    fun `rambling after the separator cannot damage the translation`() {
        // The property that made it safe to ask for both in one prompt: the
        // translation is complete before the risky half of the answer begins.
        val result = LocalTranslationParser.parse("animal | I am not sure about this one")!!
        assertEquals("animal", result.primaryTranslation)
    }

    @Test
    fun `an answer that ignores the pipe and uses commas parses the same`() {
        val result = LocalTranslationParser.parse("casa, hogar, vivienda")!!
        assertEquals("casa", result.primaryTranslation)
        assertEquals(listOf("hogar", "vivienda"), result.alternatives)
    }

    @Test
    fun `single word yields a translation with no alternatives`() {
        val result = LocalTranslationParser.parse("Haus")!!
        assertEquals("Haus", result.primaryTranslation)
        assertEquals(emptyList<String>(), result.alternatives)
    }

    @Test
    fun `trims punctuation, blanks and duplicates`() {
        val result = LocalTranslationParser.parse("\"perro\".,  perro , can ,, ")!!
        assertEquals("perro", result.primaryTranslation)
        assertEquals(listOf("can"), result.alternatives)
    }

    @Test
    fun `caps alternatives at three`() {
        val result = LocalTranslationParser.parse("a, b, c, d, e")!!
        assertEquals("a", result.primaryTranslation)
        assertEquals(listOf("b", "c", "d"), result.alternatives)
    }

    @Test
    fun `blank output yields null`() {
        assertNull(LocalTranslationParser.parse("   "))
        assertNull(LocalTranslationParser.parse(""))
    }
}
