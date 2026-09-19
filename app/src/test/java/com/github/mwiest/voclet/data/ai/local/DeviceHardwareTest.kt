package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHardwareTest {

    private val gib = 1024L * 1024L * 1024L

    @Test
    fun `each tier is suggested at its own model's requirement`() {
        AiModel.ALL.forEach { model ->
            assertEquals(
                "${model.displayName} should be suggested at exactly its own minimum",
                model.tier,
                DeviceHardware.suggestTierForRam(model.minRamBytes),
            )
        }
    }

    @Test
    fun `a nominally 8 GB phone reaches the MID rung`() {
        // As reported, not as marketed: ActivityManager says ~7.5 GiB on an
        // 8 GB device, because the kernel keeps a slice. A threshold written
        // against the round number would never be met.
        assertEquals(ModelTier.MID, DeviceHardware.suggestTierForRam(7_500L * 1024 * 1024))
        assertEquals(ModelTier.MID, DeviceHardware.suggestTierForRam(8 * gib))
    }

    @Test
    fun `the lowest rung runs on a device too small for anything else`() {
        // Translation is the everyday feature, so it must never fall back to
        // nothing.
        val small = 3 * gib
        val floor = AiModel.forTier(ModelTier.LOW)
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(small))
        assertTrue(DeviceHardware.hasRamFor(floor, small))
    }

    @Test
    fun `LOW is the floor even below its own requirement`() {
        // Something has to be suggested; the smallest model is the least bad
        // answer, and the UI says separately whether the device can carry it.
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(0L))
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(1 * gib))
    }

    @Test
    fun `suggestions never exceed what the device can carry`() {
        // Except for LOW, which is the deliberate floor.
        listOf(0L, 2 * gib, 4 * gib, 8 * gib, 12 * gib, 16 * gib, 24 * gib).forEach { ram ->
            val suggested = AiModel.forTier(DeviceHardware.suggestTierForRam(ram))
            if (suggested.tier != ModelTier.LOW) {
                assertTrue(
                    "suggested ${suggested.displayName} on ${ram / gib} GiB",
                    ram >= suggested.minRamBytes,
                )
            }
        }
    }

    @Test
    fun `hasRamFor agrees with the model's own requirement`() {
        val mid = AiModel.forTier(ModelTier.MID)
        assertTrue(DeviceHardware.hasRamFor(mid, mid.minRamBytes))
        assertFalse(DeviceHardware.hasRamFor(mid, mid.minRamBytes - 1))
    }
}
