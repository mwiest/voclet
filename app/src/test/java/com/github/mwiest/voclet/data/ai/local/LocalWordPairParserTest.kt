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
    @Test
    fun `parses two-element arrays`() {
        // InternVL3 answers like this; before, the page was read and thrown away.
        val pairs = LocalWordPairParser.parse("""[["das Haus","the house"],["laufen","to run"]]""")
        assertEquals(2, pairs.size)
        assertEquals("das Haus", pairs[0].word1)
        assertEquals("the house", pairs[0].word2)
        assertEquals("to run", pairs[1].word2)
    }

    @Test
    fun `parses one flat alternating list`() {
        // MiniCPM-V answers like this.
        val pairs = LocalWordPairParser.parse("""["das Haus","the house","laufen","to run"]""")
        assertEquals(2, pairs.size)
        assertEquals("das Haus", pairs[0].word1)
        assertEquals("to run", pairs[1].word2)
    }

    @Test
    fun `an odd flat list is rejected rather than shifted`() {
        // A missing word would shift every row after the gap, so the whole
        // answer goes rather than silently mispairing it.
        assertTrue(LocalWordPairParser.parse("""["das Haus","the house","laufen"]""").isEmpty())
        assertTrue(LocalWordPairParser.parse("""["das Haus","the house","","to run"]""").isEmpty())
    }

    @Test
    fun `a flat list of non-strings is not a word list`() {
        assertTrue(LocalWordPairParser.parse("""[1,2,3,4]""").isEmpty())
    }

    @Test
    fun `objects win over the other shapes when both are present`() {
        val pairs = LocalWordPairParser.parse(
            """[{"word1":"cat","word2":"gato"},["dog","perro"]]""",
        )
        assertEquals(1, pairs.size)
        assertEquals("cat", pairs[0].word1)
    }

    @Test
    fun `arrays of the wrong length and null sides are dropped`() {
        val pairs = LocalWordPairParser.parse("""[["a"],["b","c"],["d","e","f"],[null,"g"]]""")
        assertEquals(1, pairs.size)
        assertEquals("b", pairs[0].word1)
    }

    @Test
    fun `a null word is not the string null`() {
        assertTrue(LocalWordPairParser.parse("""[{"word1":"cat","word2":null}]""").isEmpty())
    }
}
