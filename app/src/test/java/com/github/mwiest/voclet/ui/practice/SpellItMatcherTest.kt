package com.github.mwiest.voclet.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellItMatcherTest {

    private fun assertCorrect(expected: String, user: String) {
        val result = SpellItMatcher.matches(expected, user)
        assertTrue("Expected '$user' to match '$expected'", result.isCorrect)
    }

    private fun assertWrong(expected: String, user: String) {
        val result = SpellItMatcher.matches(expected, user)
        assertFalse("Expected '$user' NOT to match '$expected'", result.isCorrect)
    }

    // --- First-character case relaxation -----------------------------------

    @Test
    fun `first char case is relaxed for keyboard auto-capitalization`() {
        assertCorrect("l'assiette", "L'assiette")
        assertCorrect("das Schloss", "Das Schloss")
        assertCorrect("the key", "The key")
    }

    @Test
    fun `case is strict mid-word`() {
        assertWrong("das Schloss", "das schloss")
        assertWrong("das Schloss", "Das schloss")
    }

    @Test
    fun `case is strict on first char of each candidate when not at start of input`() {
        // Mid-candidate uppercase K is wrong.
        assertWrong("the key / the castle", "The Key")
    }

    // --- Parenthetical stripping -------------------------------------------

    @Test
    fun `parentheticals are stripped from expected`() {
        assertCorrect("l'assiette (f.)", "l'assiette")
        assertCorrect("l'assiette (f.)", "L'assiette")
    }

    @Test
    fun `multi-solution with parentheticals on each`() {
        assertCorrect("the key (n.) / the castle (n.)", "the key")
        assertCorrect("the key (n.) / the castle (n.)", "the castle")
        assertCorrect("the key (n.) / the castle (n.)", "the key, the castle")
    }

    @Test
    fun `bracketed sub-expressions are stripped`() {
        assertCorrect("foo [optional]", "foo")
    }

    @Test
    fun `expected with only parenthetical content is never matched`() {
        // After stripping, expected has no candidates → always wrong.
        assertWrong("(see also: foo)", "")
        assertWrong("(see also: foo)", "foo")
    }

    // --- Apostrophe variants ----------------------------------------------

    @Test
    fun `curly apostrophes are normalized to ASCII apostrophe`() {
        // expected uses curly, user uses straight
        assertCorrect("l’assiette", "l'assiette")
        // expected uses straight, user uses curly
        assertCorrect("l'assiette", "l’assiette")
    }

    // --- Whitespace --------------------------------------------------------

    @Test
    fun `whitespace is collapsed and trimmed`() {
        assertCorrect("the  key", "the key")
        assertCorrect("  the key  ", "the key")
        assertCorrect("the key", "the   key")
    }

    // --- Multi-solution subset matching ------------------------------------

    @Test
    fun `single candidate from multi-solution accepted`() {
        assertCorrect("the key / the castle", "the key")
        assertCorrect("the key / the castle", "the castle")
    }

    @Test
    fun `multiple candidates from multi-solution accepted in any order`() {
        assertCorrect("the key / the castle", "the castle, the key")
        assertCorrect("the key / the castle", "the key, the castle")
    }

    @Test
    fun `non-matching candidate rejects whole submission`() {
        assertWrong("the key / the castle", "the door")
        assertWrong("the key / the castle", "the key, the door")
    }

    @Test
    fun `empty user input is rejected`() {
        assertWrong("the key", "")
        assertWrong("the key / the castle", "")
    }

    @Test
    fun `different separators are equivalent`() {
        assertCorrect("a / b", "a")
        assertCorrect("a , b", "a")
        assertCorrect("a ; b", "a")
        assertCorrect("a | b", "a")
    }

    // --- NFC vs NFD --------------------------------------------------------

    @Test
    fun `NFC composed and NFD decomposed accents compare equal`() {
        val composed = "café"           // é as single code point
        val decomposed = "café"        // e + combining acute
        assertCorrect(composed, decomposed)
        assertCorrect(decomposed, composed)
    }

    // --- Diacritic strictness ---------------------------------------------

    @Test
    fun `diacritics are required - cafe is not cafe with accent`() {
        assertWrong("café", "cafe")
        assertWrong("cafe", "café")
    }

    @Test
    fun `typo is wrong - no Levenshtein tolerance`() {
        assertWrong("the key / the castle", "the caste, the key")
        assertWrong("castle", "caste")
    }

    // --- Trailing punctuation ---------------------------------------------

    @Test
    fun `trailing sentence punctuation is stripped`() {
        assertCorrect("Hello.", "Hello")
        assertCorrect("Hello", "Hello.")
        assertCorrect("Really?", "really")
    }

    // --- Canonical exposed for diff ---------------------------------------

    @Test
    fun `canonical is returned for display`() {
        val result = SpellItMatcher.matches("l'assiette (f.)", "L'assiette")
        assertTrue(result.isCorrect)
        assertEquals("l'assiette", result.canonical)
    }

    @Test
    fun `closest candidate is picked for diff on wrong answer`() {
        // "the caste" is closer to "the castle" than "the key"
        val result = SpellItMatcher.matches("the key / the castle", "the caste")
        assertFalse(result.isCorrect)
        assertEquals("the castle", result.matchedCandidate)
    }
}
