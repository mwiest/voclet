package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceHardwareTest {

    private val gib = 1024L * 1024L * 1024L

    @Test
    fun `12 GB or more suggests HIGH tier`() {
        assertEquals(ModelTier.HIGH, DeviceHardware.suggestTierForRam(12 * gib))
        assertEquals(ModelTier.HIGH, DeviceHardware.suggestTierForRam(16 * gib))
    }

    @Test
    fun `6 to 12 GB suggests MID tier`() {
        assertEquals(ModelTier.MID, DeviceHardware.suggestTierForRam(6 * gib))
        assertEquals(ModelTier.MID, DeviceHardware.suggestTierForRam(8 * gib))
        // Just under 12 GB stays MID.
        assertEquals(ModelTier.MID, DeviceHardware.suggestTierForRam(12 * gib - 1))
    }

    @Test
    fun `under 6 GB suggests LOW tier`() {
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(0L))
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(4 * gib))
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(6 * gib - 1))
    }
}
