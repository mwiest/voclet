package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWordPairParserTest {

    @Test
    fun `parses a clean json array`() {
        val pairs = LocalWordPairParser.parse("""[{"word1":"hello","word2":"hola"},{"word1":"bye","word2":"adios"}]""")
        assertEquals(2, pairs.size)
        assertEquals("hello", pairs[0].word1)
        assertEquals("hola", pairs[0].word2)
        assertEquals("adios", pairs[1].word2)
    }

    @Test
    fun `strips surrounding prose and markdown fences`() {
        val raw = """
            Sure! Here are the pairs:
            ```json
            [{"word1":"cat","word2":"gato"}]
            ```
            Hope that helps.
        """.trimIndent()
        val pairs = LocalWordPairParser.parse(raw)
        assertEquals(1, pairs.size)
        assertEquals("gato", pairs[0].word2)
    }

    @Test
    fun `drops pairs with an empty side and trims whitespace`() {
        val pairs = LocalWordPairParser.parse("""[{"word1":" dog ","word2":"perro"},{"word1":"x","word2":""}]""")
        assertEquals(1, pairs.size)
        assertEquals("dog", pairs[0].word1)
    }

    @Test
    fun `malformed or empty output yields empty list`() {
        assertTrue(LocalWordPairParser.parse("not json at all").isEmpty())
        assertTrue(LocalWordPairParser.parse("").isEmpty())
        assertTrue(LocalWordPairParser.parse("[ {broken").isEmpty())
    }
}
