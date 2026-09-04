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
    fun `a nominally 8 GB phone is not offered the 2_2B model`() {
        // The regression this pins. ActivityManager reports ~7.5 GiB on an 8 GB
        // device, the old threshold was 6 GiB, and the model needs 10 - so every
        // 8 GB phone was told to run one that thrashes it.
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(7_500L * 1024 * 1024))
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(8 * gib))
    }

    @Test
    fun `a 12 GB phone gets MID and a 16 GB phone gets HIGH`() {
        // Again as reported, not as marketed: ~11.3 and ~15 GiB.
        assertEquals(ModelTier.MID, DeviceHardware.suggestTierForRam(11_300L * 1024 * 1024))
        assertEquals(ModelTier.HIGH, DeviceHardware.suggestTierForRam(15 * gib))
    }

    @Test
    fun `LOW is the floor even below its own requirement`() {
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
