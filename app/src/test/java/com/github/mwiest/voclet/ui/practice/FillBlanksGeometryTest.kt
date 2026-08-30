package com.github.mwiest.voclet.ui.practice

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the Fill Blanks slot geometry.
 *
 * Slots no longer carry predicted coordinates: the row of cards that draws them reports where each
 * one actually landed, and a drop is matched against those reported centres. These tests pin that
 * contract, in particular the cases where a centre is missing or the slot is already taken.
 */
class FillBlanksGeometryTest {

    // Density 1 keeps the threshold readable: LETTER_CARD_SIZE * 1.5 = 84dp = 84px.
    private val density = 1f
    private val threshold = LETTER_CARD_SIZE.value * 1.5f

    private fun statesFor(word: String, filled: Set<Int> = emptySet()): List<LetterSlotState> =
        generateLetterSlots(word).mapIndexed { index, slot ->
            LetterSlotState(
                letterSlot = slot,
                placedLetter = if (index in filled) slot.letter else null
            )
        }

    @Test
    fun `generateLetterSlots names every letter in order`() {
        val slots = generateLetterSlots("chat")

        assertEquals(4, slots.size)
        assertEquals(listOf('c', 'h', 'a', 't'), slots.map { it.letter })
        assertEquals(listOf(0, 1, 2, 3), slots.map { it.index })
    }

    @Test
    fun `generateLetterSlots on an empty word yields no slots`() {
        assertEquals(emptyList<LetterSlot>(), generateLetterSlots(""))
    }

    @Test
    fun `nearest slot is the closest measured centre`() {
        val centers = mapOf(
            0 to Offset(0f, 0f),
            1 to Offset(100f, 0f),
            2 to Offset(200f, 0f)
        )

        val hit = findNearestLetterSlot(
            positionPx = Offset(110f, 5f),
            letterSlotStates = statesFor("abc"),
            slotCentersPx = centers,
            density = density
        )

        assertEquals(1, hit)
    }

    @Test
    fun `a position beyond the threshold matches nothing`() {
        val centers = mapOf(0 to Offset(0f, 0f))

        val hit = findNearestLetterSlot(
            positionPx = Offset(threshold + 1f, 0f),
            letterSlotStates = statesFor("a"),
            slotCentersPx = centers,
            density = density
        )

        assertNull(hit)
    }

    @Test
    fun `a filled slot is never targeted even when it is nearest`() {
        val centers = mapOf(
            0 to Offset(0f, 0f),
            1 to Offset(60f, 0f)
        )

        val hit = findNearestLetterSlot(
            positionPx = Offset(5f, 0f),
            letterSlotStates = statesFor("ab", filled = setOf(0)),
            slotCentersPx = centers,
            density = density
        )

        // Slot 0 is closest but taken, so the drop falls through to slot 1 within the threshold.
        assertEquals(1, hit)
    }

    @Test
    fun `a slot that has not been measured yet cannot be targeted`() {
        // Slot 0 has no reported centre - it has not been laid out yet.
        val centers = mapOf(1 to Offset(300f, 0f))

        val hit = findNearestLetterSlot(
            positionPx = Offset(0f, 0f),
            letterSlotStates = statesFor("ab"),
            slotCentersPx = centers,
            density = density
        )

        assertNull(hit)
    }

    @Test
    fun `centres are compared in two dimensions, not just horizontally`() {
        val centers = mapOf(
            0 to Offset(0f, 0f),
            1 to Offset(0f, 100f)
        )

        val hit = findNearestLetterSlot(
            positionPx = Offset(0f, 90f),
            letterSlotStates = statesFor("ab"),
            slotCentersPx = centers,
            density = density
        )

        assertEquals(1, hit)
    }

    @Test
    fun `density scales the threshold`() {
        val centers = mapOf(0 to Offset(0f, 0f))
        val justBeyondAtDensityOne = Offset(threshold + 10f, 0f)

        assertNull(
            findNearestLetterSlot(
                positionPx = justBeyondAtDensityOne,
                letterSlotStates = statesFor("a"),
                slotCentersPx = centers,
                density = 1f
            )
        )

        // The same distance is well within reach once each dp is worth two pixels.
        assertEquals(
            0,
            findNearestLetterSlot(
                positionPx = justBeyondAtDensityOne,
                letterSlotStates = statesFor("a"),
                slotCentersPx = centers,
                density = 2f
            )
        )
    }
}
