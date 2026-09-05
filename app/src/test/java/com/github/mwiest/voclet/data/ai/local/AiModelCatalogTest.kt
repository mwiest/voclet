package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelCatalogTest {

    private val GIB = 1024L * 1024L * 1024L

    @Test
    fun `vision offers a tier ladder and text deliberately does not`() {
        // Vision spans the tiers because reading a page really does get better
        // with a bigger model. Text is a single entry on purpose: the one model
        // that fits the smallest device also scores as well as anything larger,
        // so a ladder would only add a bottom rung that answers badly.
        val visionTiers = AiModel.VISION.map { it.tier }
        assertEquals("vision is missing a tier", ModelTier.entries.toSet(), visionTiers.toSet())
        assertEquals("vision has duplicate tiers", ModelTier.entries.size, visionTiers.size)

        assertEquals("text is meant to be one model for every device", 1, AiModel.TEXT.size)
    }

    @Test
    fun `the one text model fits the devices the vision ladder starts at`() {
        // The claim that makes a single text entry safe: nothing needs a smaller
        // fallback. If a future swap raises this above the smallest vision
        // model's bar, some device can read a photo but not translate a word.
        val text = AiModel.TEXT.single()
        val smallestVision = AiModel.VISION.minByOrNull { it.minRamBytes }!!
        assertTrue(
            "${text.id} needs more RAM than ${smallestVision.id}, so it is not universal",
            text.minRamBytes <= smallestVision.minRamBytes + GIB,
        )
    }

    @Test
    fun `every model declares the kind of the catalog it sits in`() {
        // The field and the list have to agree, or forKind and the model's own
        // kind disagree about where a model belongs - and the screen groups by
        // one while the engine picks by the other.
        ModelKind.entries.forEach { kind ->
            AiModel.forKind(kind).forEach { assertEquals(kind, it.kind) }
        }
        assertEquals(AiModel.ALL.size, AiModel.TEXT.size + AiModel.VISION.size)
    }

    @Test
    fun `model ids and weights file names are unique across both catalogs`() {
        // Across both, not within one: the ids key WorkManager jobs and the file
        // names share a single models directory, so a collision between a text
        // and a vision model is exactly as damaging as one within a kind.
        assertEquals(AiModel.ALL.size, AiModel.ALL.map { it.id }.toSet().size)
        val weights = AiModel.ALL.map { it.ggufFileName }
        assertEquals("two models must not collide on a weights file", weights.size, weights.toSet().size)
    }

    @Test
    fun `text models carry no projector and vision models carry a complete one`() {
        AiModel.TEXT.forEach { model ->
            assertNull("${model.id} declares a projector url", model.mmprojUrl)
            assertNull("${model.id} declares a projector file", model.mmprojFileName)
            assertNull("${model.id} declares a projector size", model.mmprojSizeBytes)
        }
        AiModel.VISION.forEach { model ->
            assertNotNull("${model.id} has no projector url", model.mmprojUrl)
            assertNotNull("${model.id} has no projector file", model.mmprojFileName)
            assertNotNull("${model.id} has no projector size", model.mmprojSizeBytes)
        }
    }

    @Test
    fun `a shared projector always comes from the same url`() {
        // MID and HIGH are one model at two quantizations, so they legitimately
        // share a projector file. What must not happen is the same file name
        // being fetched from two different places.
        AiModel.VISION.groupBy { it.mmprojFileName }.forEach { (name, models) ->
            assertEquals("$name is fetched from more than one url", 1, models.map { it.mmprojUrl }.toSet().size)
        }
    }

    @Test
    fun `sizes are real byte counts, not round numbers`() {
        AiModel.ALL.forEach { model ->
            assertTrue("${model.id} weights size looks unset", model.ggufSizeBytes > 0)
            assertEquals(
                model.ggufSizeBytes + (model.mmprojSizeBytes ?: 0L),
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
        AiModel.VISION.forEach { model ->
            assertTrue(
                "${model.id} weight ${model.ggufProgressWeight} is outside a plausible range",
                model.ggufProgressWeight > 0.5f && model.ggufProgressWeight < 0.95f,
            )
        }
        // A weights-only model is the whole download, so progress must run to
        // 1.0 on that file alone rather than stalling at some projector split.
        AiModel.TEXT.forEach { model ->
            assertEquals("${model.id} is weights-only", 1f, model.ggufProgressWeight, 0.0001f)
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
        // Within a kind, not across: a text model and a vision model of similar
        // size sit at different thresholds because only one of them also maps a
        // projector, so a single global ordering would be meaningless.
        ModelKind.entries.forEach { kind ->
            val models = AiModel.forKind(kind)
            assertEquals(
                "$kind ram thresholds do not follow size order",
                models.sortedBy { it.approxSizeBytes },
                models.sortedBy { it.minRamBytes },
            )
        }
    }

    @Test
    fun `byId resolves ids from both catalogs and rejects unknown`() {
        assertNotNull(AiModel.byId("smolvlm-256m"))
        assertNotNull(AiModel.byId("lfm2-700m"))
        assertNull(AiModel.byId("does-not-exist"))
    }

    @Test
    fun `forTier returns the matching model of the requested kind`() {
        val text = AiModel.forTier(ModelKind.TEXT, ModelTier.LOW)
        assertEquals(ModelTier.LOW, text.tier)
        assertEquals(ModelKind.TEXT, text.kind)

        val vision = AiModel.forTier(ModelKind.VISION, ModelTier.HIGH)
        assertEquals(ModelTier.HIGH, vision.tier)
        assertEquals(ModelKind.VISION, vision.kind)
    }

    @Test
    fun `urls point at resolvable gguf files`() {
        AiModel.ALL.forEach { model ->
            assertTrue(model.ggufUrl.startsWith("https://"))
            assertTrue(model.ggufUrl.endsWith(model.ggufFileName))
            model.mmprojUrl?.let { assertTrue(it.endsWith(model.mmprojFileName!!)) }
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

    @Test
    fun `the smallest text model is cheaper than the smallest vision model`() {
        // The point of the split, stated as a fact about the catalog: a user who
        // only ever types words must not be paying for a vision projector.
        val text = AiModel.forTier(ModelKind.TEXT, ModelTier.LOW)
        val vision = AiModel.forTier(ModelKind.VISION, ModelTier.MID)
        assertTrue(
            "${text.id} (${text.approxSizeBytes}) should undercut ${vision.id}",
            text.approxSizeBytes < vision.approxSizeBytes,
        )
    }
}
