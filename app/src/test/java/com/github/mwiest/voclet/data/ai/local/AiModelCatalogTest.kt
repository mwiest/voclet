package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelCatalogTest {

    @Test
    fun `the tier ladder stops where the evidence stops`() {
        // No HIGH rung, and that is the point: every model larger than the MID
        // entry measured *worse* at translation, so a HIGH tier could only be
        // filled for symmetry. suggestTierForRam reads the catalog, so an absent
        // tier is simply never suggested - but a duplicate one would make
        // forTier pick arbitrarily between two models.
        val tiers = AiModel.ALL.map { it.tier }
        assertEquals("duplicate tiers", tiers.size, tiers.toSet().size)
        assertTrue("a LOW rung is the floor", tiers.contains(ModelTier.LOW))
    }

    @Test
    fun `model ids and weights file names are unique`() {
        // The ids key WorkManager jobs and the file names share a single models
        // directory with every other bundle, so either kind of collision is
        // damaging.
        assertEquals(AiModel.ALL.size, AiModel.ALL.map { it.id }.toSet().size)
        val weights = AiModel.ALL.map { it.ggufFileName }
        assertEquals("two models must not collide on a weights file", weights.size, weights.toSet().size)
    }

    @Test
    fun `sizes are real byte counts, not round numbers`() {
        AiModel.ALL.forEach { model ->
            assertTrue("${model.id} weights size looks unset", model.ggufSizeBytes > 0)
            assertEquals(model.ggufSizeBytes, model.totalSizeBytes)
            // A size rounded to a whole MiB is the signature of an estimate; the
            // catalog is meant to carry exact blob sizes from the HF API.
            assertTrue(
                "${model.id} size ${model.totalSizeBytes} is suspiciously round",
                model.totalSizeBytes % (1024L * 1024L) != 0L,
            )
        }
    }

    @Test
    fun `every model presents its files with real sizes that add up`() {
        // ModelDownloader weights the progress bar by these sizes, so a zero or
        // a guess here shows up as a bar that jumps or stalls.
        AiModel.ALL.forEach { model ->
            assertEquals("${model.id} is weights-only", 1, model.files.size)
            model.files.forEach { file ->
                assertTrue("${model.id}/${file.fileName} has no size", file.sizeBytes > 0)
                assertTrue(
                    "${model.id} url does not end in ${file.fileName}, so it is not pinned",
                    file.url.endsWith(file.fileName),
                )
            }
            assertEquals(
                "${model.id} total",
                model.files.sumOf { it.sizeBytes },
                model.totalSizeBytes,
            )
        }
    }

    @Test
    fun `ram requirements rise with model size and clear roughly 6x`() {
        AiModel.ALL.forEach { model ->
            assertTrue(
                "${model.id} needs ${model.minRamBytes} for ${model.totalSizeBytes} on disk",
                model.minRamBytes >= 5 * model.totalSizeBytes,
            )
        }
        assertEquals(
            "ram thresholds do not follow size order",
            AiModel.ALL.sortedBy { it.totalSizeBytes },
            AiModel.ALL.sortedBy { it.minRamBytes },
        )
    }

    @Test
    fun `byId resolves catalog ids and rejects unknown`() {
        assertNotNull(AiModel.byId("lfm2-700m"))
        assertNull(AiModel.byId("does-not-exist"))
        // The vision models are gone, not merely unlisted: an id left resolvable
        // would let a queued download or a stored preference resurrect one.
        assertNull(AiModel.byId("smolvlm-256m"))
        assertNull(AiModel.byId("smolvlm2-2.2b"))
    }

    @Test
    fun `forTier returns the model at that tier`() {
        assertEquals(ModelTier.LOW, AiModel.forTier(ModelTier.LOW).tier)
        assertEquals(ModelTier.MID, AiModel.forTier(ModelTier.MID).tier)
    }

    @Test
    fun `urls point at resolvable gguf files`() {
        AiModel.ALL.forEach { model ->
            assertTrue(model.ggufUrl.startsWith("https://"))
            assertTrue(model.ggufUrl.endsWith(model.ggufFileName))
        }
    }

    @Test
    fun `every model has somewhere to put the prompt`() {
        AiModel.ALL.forEach { model ->
            assertTrue(
                "${model.id} template has nowhere to put the prompt",
                model.promptFormat.contains(AiModel.PROMPT_PLACEHOLDER),
            )
        }
    }

    @Test
    fun `templates keep a system turn for the instruction to sit in`() {
        // The engine silently inlines the instruction when the placeholder is
        // missing, and the user turn is far worse for it - so losing this reads
        // as the model getting worse, not as a template change.
        AiModel.ALL.forEach { model ->
            assertTrue(
                "${model.id} has no system slot, so the instruction would be inlined",
                model.promptFormat.contains(AiModel.SYSTEM_PLACEHOLDER),
            )
        }
    }

    @Test
    fun `no template teaches the model a marker the cleaner would not strip`() {
        // A template puts turn markers in front of the model, and a small model
        // echoes back what it is shown. Whatever appears here therefore has to
        // be something the cleaner can remove - otherwise it reaches the user as
        // a translation, which is exactly what `<end_of_utterance>` did.
        AiModel.ALL.forEach { model ->
            Regex("<[^>]+>").findAll(model.promptFormat).forEach { match ->
                assertEquals(
                    "${match.value} is shown to ${model.id} but survives cleaning",
                    "",
                    CompletionCleaner.clean(match.value),
                )
            }
        }
    }
}
