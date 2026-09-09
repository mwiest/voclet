package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalTranslationParserTest {

    @Test
    fun `takes the translation and drops what the model padded after it`() {
        // The reason this is not a list: the prompt asks for one translation,
        // so "banknote" is invented, and an alternative is one tap from the
        // user's word list.
        val result = LocalTranslationParser.parse("bank, banknote")!!
        assertEquals("bank", result.primaryTranslation)
        assertEquals(emptyList<String>(), result.alternatives)
    }

    @Test
    fun `rambling after the translation cannot damage it`() {
        val result = LocalTranslationParser.parse("animal | I am not sure about this one")!!
        assertEquals("animal", result.primaryTranslation)
    }

    @Test
    fun `a prose answer keeps only its first clause`() {
        val result = LocalTranslationParser.parse("the bank, - Bank (noun): a financial institution")!!
        assertEquals("the bank", result.primaryTranslation)
    }

    @Test
    fun `single word passes through`() {
        assertEquals("Haus", LocalTranslationParser.parse("Haus")!!.primaryTranslation)
    }

    @Test
    fun `trims punctuation and leading blanks`() {
        assertEquals("perro", LocalTranslationParser.parse("\"perro\".,  perro , can ")!!.primaryTranslation)
        assertEquals("casa", LocalTranslationParser.parse(" , casa, hogar")!!.primaryTranslation)
    }

    @Test
    fun `blank output yields null`() {
        assertNull(LocalTranslationParser.parse("   "))
        assertNull(LocalTranslationParser.parse(""))
        assertNull(LocalTranslationParser.parse(" , ,, "))
    }
}
