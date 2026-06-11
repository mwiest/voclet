package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalTranslationParserTest {

    @Test
    fun `comma-separated output yields primary plus alternatives`() {
        val result = LocalTranslationParser.parse("casa, hogar, vivienda")!!
        assertEquals("casa", result.primaryTranslation)
        assertEquals(listOf("hogar", "vivienda"), result.alternatives)
    }

    @Test
    fun `single word yields primary with no alternatives`() {
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
        assertEquals(listOf("b", "c", "d"), result.alternatives)
    }

    @Test
    fun `blank output yields null`() {
        assertNull(LocalTranslationParser.parse("   "))
        assertNull(LocalTranslationParser.parse(",,, ; \n"))
    }
}
