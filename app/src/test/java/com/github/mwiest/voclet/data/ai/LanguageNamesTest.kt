package com.github.mwiest.voclet.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageNamesTest {

    @Test
    fun `iso codes resolve to english names`() {
        assertEquals("German", LanguageNames.englishName("de"))
        assertEquals("English", LanguageNames.englishName("en"))
        assertEquals("French", LanguageNames.englishName("fr"))
        assertEquals("Spanish", LanguageNames.englishName("es"))
    }

    @Test
    fun `regional tags resolve to the base language`() {
        assertEquals("English", LanguageNames.englishName("en-GB"))
        assertEquals("Portuguese", LanguageNames.englishName("pt-BR"))
    }

    @Test
    fun `names are always in english, never the native form`() {
        // The Language model carries nativeName ("Deutsch"), which would put two
        // languages in one prompt; the prompt wants one.
        assertEquals("German", LanguageNames.englishName("de"))
    }

    @Test
    fun `a name passes through unchanged`() {
        assertEquals("German", LanguageNames.englishName("German"))
        assertEquals("Klingon", LanguageNames.englishName("Klingon"))
    }

    @Test
    fun `a name is not mangled into a language subtag`() {
        // Locale treats any 2-8 letter word as a well-formed language subtag and
        // canonicalizes it, which turned "German" into "german".
        assertEquals("Dutch", LanguageNames.englishName("Dutch"))
        assertEquals("Latin", LanguageNames.englishName("Latin"))
    }

    @Test
    fun `unknown and malformed input falls back to the input`() {
        // Voclet is language-agnostic, so an unrecognized code must still reach
        // the model rather than being dropped.
        assertEquals("zz", LanguageNames.englishName("zz"))
        assertEquals("!!", LanguageNames.englishName("!!"))
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", LanguageNames.englishName(""))
        assertEquals("", LanguageNames.englishName("   "))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("German", LanguageNames.englishName("  de  "))
    }
}
