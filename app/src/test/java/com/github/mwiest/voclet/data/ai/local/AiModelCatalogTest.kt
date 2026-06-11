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
    fun `model ids are unique and file names distinct`() {
        assertEquals(AiModel.ALL.size, AiModel.ALL.map { it.id }.toSet().size)
        val allFiles = AiModel.ALL.flatMap { listOf(it.ggufFileName, it.mmprojFileName) }
        assertEquals(allFiles.size, allFiles.toSet().size)
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
