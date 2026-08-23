package com.github.mwiest.voclet.data.ai.cloud

import com.github.mwiest.voclet.data.ai.CloudAiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudResponseParserTest {

    @Test
    fun `parses a clean word pair extraction`() {
        val result = CloudResponseParser.parseWordPairs(
            """
            {
              "title": "Unit 3",
              "detectedLanguage1": "en",
              "detectedLanguage2": "es",
              "wordPairs": [
                {"word1": "hello", "word2": "hola", "confidence": 0.95},
                {"word1": "goodbye", "word2": "adiós", "confidence": 0.9}
              ],
              "confidence": 0.92
            }
            """.trimIndent(),
        ).getOrThrow()

        assertEquals("Unit 3", result.title)
        assertEquals("en", result.detectedLanguage1)
        assertEquals("es", result.detectedLanguage2)
        assertEquals(2, result.wordPairs.size)
        assertEquals("hola", result.wordPairs[0].word2)
        assertEquals(0.9f, result.wordPairs[1].confidence, 0.001f)
    }

    @Test
    fun `strips a markdown fence and surrounding prose`() {
        val result = CloudResponseParser.parseWordPairs(
            """
            Sure! Here is the vocabulary I found:
            ```json
            {"detectedLanguage1":"de","detectedLanguage2":"fr","wordPairs":[{"word1":"Haus","word2":"maison"}]}
            ```
            Let me know if you need more.
            """.trimIndent(),
        ).getOrThrow()

        assertEquals("de", result.detectedLanguage1)
        assertEquals(1, result.wordPairs.size)
        assertEquals("Haus", result.wordPairs[0].word1)
    }

    @Test
    fun `treats a literal null title as absent`() {
        val result = CloudResponseParser.parseWordPairs(
            """{"title":"null","detectedLanguage1":"en","detectedLanguage2":"es","wordPairs":[]}""",
        ).getOrThrow()

        assertNull(result.title)
        assertTrue(result.wordPairs.isEmpty())
    }

    @Test
    fun `drops half-empty pairs and trims whitespace`() {
        val result = CloudResponseParser.parseWordPairs(
            """
            {"detectedLanguage1":"en","detectedLanguage2":"es","wordPairs":[
              {"word1":"  water ","word2":" agua "},
              {"word1":"orphan","word2":""}
            ]}
            """.trimIndent(),
        ).getOrThrow()

        assertEquals(1, result.wordPairs.size)
        assertEquals("water", result.wordPairs[0].word1)
        assertEquals("agua", result.wordPairs[0].word2)
    }

    @Test
    fun `reports a parse error when there is no json object`() {
        val error = CloudResponseParser.parseWordPairs("I cannot read this image.").exceptionOrNull()
        assertTrue(error is CloudAiException.ParseError)
    }

    @Test
    fun `parses a translation with alternatives and notes`() {
        val suggestion = CloudResponseParser.parseTranslation(
            """
            {"primaryTranslation":"hola","alternatives":["buenos días","qué tal"],
             "contextualNotes":"Informal greeting"}
            """.trimIndent(),
        ).getOrThrow()

        assertEquals("hola", suggestion.primaryTranslation)
        assertEquals(listOf("buenos días", "qué tal"), suggestion.alternatives)
        assertEquals("Informal greeting", suggestion.contextualNotes)
    }

    @Test
    fun `translation without notes or alternatives still parses`() {
        val suggestion = CloudResponseParser.parseTranslation(
            """{"primaryTranslation":"agua","contextualNotes":null}""",
        ).getOrThrow()

        assertEquals("agua", suggestion.primaryTranslation)
        assertTrue(suggestion.alternatives.isEmpty())
        assertNull(suggestion.contextualNotes)
    }

    @Test
    fun `an empty primary translation is a parse error`() {
        val error = CloudResponseParser.parseTranslation(
            """{"primaryTranslation":"  "}""",
        ).exceptionOrNull()
        assertTrue(error is CloudAiException.ParseError)
    }
}
