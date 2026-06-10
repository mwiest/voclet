package com.github.mwiest.voclet.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellItDiffTest {

    @Test
    fun `equal strings produce all matches`() {
        val ops = SpellItDiff.diff("castle", "castle")
        assertEquals(6, ops.size)
        assertTrue(ops.all { it is DiffOp.Match })
        assertEquals("castle", ops.joinToString("") { (it as DiffOp.Match).char.toString() })
    }

    @Test
    fun `empty user input yields all missing`() {
        val ops = SpellItDiff.diff("castle", "")
        assertEquals(6, ops.size)
        assertTrue(ops.all { it is DiffOp.Missing })
        assertEquals("castle", ops.joinToString("") { (it as DiffOp.Missing).char.toString() })
    }

    @Test
    fun `empty expected yields all wrong`() {
        val ops = SpellItDiff.diff("", "xyz")
        assertEquals(3, ops.size)
        assertTrue(ops.all { it is DiffOp.Wrong })
    }

    @Test
    fun `both empty yields empty`() {
        assertTrue(SpellItDiff.diff("", "").isEmpty())
    }

    @Test
    fun `single insertion - user missed one char`() {
        // expected "castle" vs user "caste" → missing "l"
        val ops = SpellItDiff.diff("castle", "caste")
        assertEquals(6, ops.size)
        // "c","a","s","t",missing("l"),"e"
        assertEquals(DiffOp.Match('c'), ops[0])
        assertEquals(DiffOp.Match('a'), ops[1])
        assertEquals(DiffOp.Match('s'), ops[2])
        assertEquals(DiffOp.Match('t'), ops[3])
        assertEquals(DiffOp.Missing('l'), ops[4])
        assertEquals(DiffOp.Match('e'), ops[5])
    }

    @Test
    fun `single deletion - user typed extra char`() {
        // expected "cat" vs user "cart" → extra "r"
        val ops = SpellItDiff.diff("cat", "cart")
        // "c","a",wrong("r"),"t"
        assertEquals(4, ops.size)
        assertEquals(DiffOp.Match('c'), ops[0])
        assertEquals(DiffOp.Match('a'), ops[1])
        assertEquals(DiffOp.Wrong('r'), ops[2])
        assertEquals(DiffOp.Match('t'), ops[3])
    }

    @Test
    fun `single substitution - one wrong char`() {
        // expected "cat" vs user "bat" → substitute c→b
        val ops = SpellItDiff.diff("cat", "bat")
        // Per spec: substitution renders as Wrong(userChar) + Missing(expectedChar)
        assertEquals(4, ops.size)
        assertEquals(DiffOp.Wrong('b'), ops[0])
        assertEquals(DiffOp.Missing('c'), ops[1])
        assertEquals(DiffOp.Match('a'), ops[2])
        assertEquals(DiffOp.Match('t'), ops[3])
    }

    @Test
    fun `multi-char diff`() {
        // expected "castle" vs user "caste" → already covered, add "kettle" vs "kettel"
        val ops = SpellItDiff.diff("kettle", "kettel")
        // Walking the DP: k-e-t-t are matches. Then expected has "le" user has "el".
        // The result depends on how DP breaks ties. Sanity-check that:
        //  - all matching chars at the start are matches
        //  - the total Wrong+Missing count matches the edit distance (which is 2)
        val matchCount = ops.count { it is DiffOp.Match }
        val wrongCount = ops.count { it is DiffOp.Wrong }
        val missingCount = ops.count { it is DiffOp.Missing }
        assertEquals(4, matchCount) // k, e, t, t
        // 2 substitutions = 2 wrong + 2 missing, OR equivalent 1 swap representation
        assertTrue("edits should sum to 4 (two subs)", wrongCount + missingCount == 4 ||
                (wrongCount + missingCount == 2)) // tolerate tie-breaking variants
    }

    @Test
    fun `wrong chars at start of user input`() {
        val ops = SpellItDiff.diff("hello", "Xhello")
        assertEquals(6, ops.size)
        // Walking: extra 'X' first, then five matches
        assertEquals(DiffOp.Wrong('X'), ops[0])
        for (i in 1..5) {
            assertTrue("op $i should be Match", ops[i] is DiffOp.Match)
        }
    }

    @Test
    fun `missing chars at start of expected`() {
        val ops = SpellItDiff.diff("Xhello", "hello")
        assertEquals(6, ops.size)
        assertEquals(DiffOp.Missing('X'), ops[0])
        for (i in 1..5) {
            assertTrue("op $i should be Match", ops[i] is DiffOp.Match)
        }
    }
}
