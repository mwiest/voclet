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
                DeviceHardware.suggestTierForRam(model.kind, model.minRamBytes),
            )
        }
    }

    @Test
    fun `a nominally 8 GB phone is not offered the 2_2B vision model`() {
        // The regression this pins. ActivityManager reports ~7.5 GiB on an 8 GB
        // device, the old threshold was 6 GiB, and the model needs 10 - so every
        // 8 GB phone was told to run one that thrashes it.
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(ModelKind.VISION, 7_500L * 1024 * 1024))
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(ModelKind.VISION, 8 * gib))
    }

    @Test
    fun `the same 8 GB phone gets the better text model but the smallest vision one`() {
        // The asymmetry the per-kind split exists for. On one 8 GB device the
        // vision ladder is pinned to its floor while text reaches its MID rung,
        // because a text model carries no projector. A single shared tier would
        // have to pick one of these answers and be wrong about the other.
        val eightGb = 7_500L * 1024 * 1024
        assertEquals(ModelTier.MID, DeviceHardware.suggestTierForRam(ModelKind.TEXT, eightGb))
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(ModelKind.VISION, eightGb))
    }

    @Test
    fun `the lowest text rung runs on a device too small for anything else`() {
        // Translation is the everyday feature, so it must never fall back to
        // nothing. 3 GiB is below every other model's bar, including vision's.
        val small = 3 * gib
        val floor = AiModel.forTier(ModelKind.TEXT, ModelTier.LOW)
        assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(ModelKind.TEXT, small))
        assertTrue(DeviceHardware.hasRamFor(floor, small))
    }

    @Test
    fun `a 12 GB phone gets vision MID and a 16 GB phone gets vision HIGH`() {
        // Again as reported, not as marketed: ~11.3 and ~15 GiB.
        assertEquals(ModelTier.MID, DeviceHardware.suggestTierForRam(ModelKind.VISION, 11_300L * 1024 * 1024))
        assertEquals(ModelTier.HIGH, DeviceHardware.suggestTierForRam(ModelKind.VISION, 15 * gib))
    }

    @Test
    fun `LOW is the floor for both kinds even below its own requirement`() {
        ModelKind.entries.forEach { kind ->
            assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(kind, 0L))
            assertEquals(ModelTier.LOW, DeviceHardware.suggestTierForRam(kind, 1 * gib))
        }
    }

    @Test
    fun `suggestions never exceed what the device can carry`() {
        // Except for LOW, which is the deliberate floor.
        val rams = listOf(0L, 2 * gib, 4 * gib, 8 * gib, 12 * gib, 16 * gib, 24 * gib)
        ModelKind.entries.forEach { kind ->
            rams.forEach { ram ->
                val suggested = AiModel.forTier(kind, DeviceHardware.suggestTierForRam(kind, ram))
                if (suggested.tier != ModelTier.LOW) {
                    assertTrue(
                        "suggested ${suggested.displayName} on ${ram / gib} GiB",
                        ram >= suggested.minRamBytes,
                    )
                }
            }
        }
    }

    @Test
    fun `hasRamFor agrees with the model's own requirement`() {
        val mid = AiModel.forTier(ModelKind.VISION, ModelTier.MID)
        assertTrue(DeviceHardware.hasRamFor(mid, mid.minRamBytes))
        assertFalse(DeviceHardware.hasRamFor(mid, mid.minRamBytes - 1))
    }
}
