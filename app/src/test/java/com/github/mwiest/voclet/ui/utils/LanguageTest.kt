package com.github.mwiest.voclet.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LanguageTest {

    @Test
    fun findsALanguageByItsCode() {
        assertEquals("fi", "fi".isoToLanguage()?.code)
    }

    @Test
    fun ignoresTheRegion() {
        assertEquals("pt", "pt-BR".isoToLanguage()?.code)
        assertEquals("nl", "nl_BE".isoToLanguage()?.code)
    }

    @Test
    fun ignoresCase() {
        assertEquals("de", "DE".isoToLanguage()?.code)
    }

    @Test
    fun readsTheNorwegianMacrolanguageAsBokmal() {
        assertEquals("nb", "no".isoToLanguage()?.code)
        assertEquals("nb", "no-NO".isoToLanguage()?.code)
    }

    @Test
    fun anUnlistedLanguageIsNotFound() {
        assertNull("ja".isoToLanguage())
        assertNull("".isoToLanguage())
    }

    @Test
    fun codesAreUnique() {
        assertEquals(LANGUAGES.size, LANGUAGES.map { it.code }.toSet().size)
    }

    /** AGENTS.md: UI text is translated into every language in LANGUAGES. */
    @Test
    fun everyLanguageHasAUiTranslation() {
        LANGUAGES.filter { it.code != "en" }.forEach { language ->
            val strings = File("src/main/res/values-${language.code}/strings.xml")
            assertTrue("missing ${strings.path}", strings.isFile)
        }
    }

    @Test
    fun everyVariantBelongsToItsLanguage() {
        LANGUAGES.forEach { language ->
            language.commonVariants.forEach { variant ->
                assertEquals(language.code, variant.code.substringBefore('-'))
            }
        }
    }
}
