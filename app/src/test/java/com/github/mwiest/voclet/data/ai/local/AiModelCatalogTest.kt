package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelCatalogTest {

    @Test
    fun `catalog has exactly one model per tier`() {
        val tiers = AiModel.ALL.map { it.tier }
        assertEquals(ModelTier.entries.toSet(), tiers.toSet())
        assertEquals("no duplicate tiers", ModelTier.entries.size, tiers.size)
    }

    @Test
    fun `model ids and weights file names are unique`() {
        assertEquals(AiModel.ALL.size, AiModel.ALL.map { it.id }.toSet().size)
        val weights = AiModel.ALL.map { it.ggufFileName }
        assertEquals("two models must not collide on a weights file", weights.size, weights.toSet().size)
    }

    @Test
    fun `a shared projector always comes from the same url`() {
        // MID and HIGH are one model at two quantizations, so they legitimately
        // share a projector file. What must not happen is the same file name
        // being fetched from two different places.
        AiModel.ALL.groupBy { it.mmprojFileName }.forEach { (name, models) ->
            assertEquals("$name is fetched from more than one url", 1, models.map { it.mmprojUrl }.toSet().size)
        }
    }

    @Test
    fun `sizes are real byte counts, not round numbers`() {
        AiModel.ALL.forEach { model ->
            assertTrue("${model.id} weights size looks unset", model.ggufSizeBytes > 0)
            assertTrue("${model.id} projector size looks unset", model.mmprojSizeBytes > 0)
            assertEquals(
                model.ggufSizeBytes + model.mmprojSizeBytes,
                model.approxSizeBytes,
            )
            // A size rounded to a whole MiB is the signature of an estimate; the
            // catalog is meant to carry exact blob sizes from the HF API.
            assertTrue(
                "${model.id} size ${model.approxSizeBytes} is suspiciously round",
                model.approxSizeBytes % (1024L * 1024L) != 0L,
            )
        }
    }

    @Test
    fun `download progress is weighted by the real file split`() {
        AiModel.ALL.forEach { model ->
            assertTrue(
                "${model.id} weight ${model.ggufProgressWeight} is outside a plausible range",
                model.ggufProgressWeight > 0.5f && model.ggufProgressWeight < 0.95f,
            )
        }
    }

    @Test
    fun `ram requirements rise with model size and clear roughly 6x`() {
        AiModel.ALL.forEach { model ->
            assertTrue(
                "${model.id} needs ${model.minRamBytes} for ${model.approxSizeBytes} on disk",
                model.minRamBytes >= 5 * model.approxSizeBytes,
            )
        }
        val bySize = AiModel.ALL.sortedBy { it.approxSizeBytes }
        assertEquals(bySize, AiModel.ALL.sortedBy { it.minRamBytes })
    }

    @Test
    fun `byId resolves known ids and rejects unknown`() {
        assertNotNull(AiModel.byId("smolvlm-256m"))
        assertNull(AiModel.byId("does-not-exist"))
    }

    @Test
    fun `forTier returns the matching model`() {
        assertEquals(ModelTier.LOW, AiModel.forTier(ModelTier.LOW).tier)
        assertEquals(ModelTier.HIGH, AiModel.forTier(ModelTier.HIGH).tier)
    }

    @Test
    fun `urls point at resolvable gguf files`() {
        AiModel.ALL.forEach { model ->
            assertTrue(model.ggufUrl.startsWith("https://"))
            assertTrue(model.ggufUrl.endsWith(model.ggufFileName))
            assertTrue(model.mmprojUrl.endsWith(model.mmprojFileName))
        }
    }
}
