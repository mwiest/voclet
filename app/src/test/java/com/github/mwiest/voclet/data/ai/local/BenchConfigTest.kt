package com.github.mwiest.voclet.data.ai.local

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins `tools/llm-bench/bench.json`'s baseline prompt to the one the app
 * actually sends.
 *
 * The bench measures every candidate prompt against that baseline, so if the
 * two drift apart the whole comparison silently grades the wrong thing —
 * nothing fails, the numbers just stop meaning what they say. Making it a test
 * turns that into a diff someone has to look at.
 */
class BenchConfigTest {

    @Test
    fun `the bench baseline prompt is the one the app sends`() {
        val config = benchConfig() ?: return  // tools/ absent: nothing to check
        val shipped = LlmPrompts.translation("Haus", "de", "en").system
            .replace("German", "{from}")
            .replace("English", "{to}")

        assertTrue(
            "tools/llm-bench/bench.json no longer carries the shipped prompt.\n" +
                "Update its \"P1 shipped\" system string to:\n  $shipped",
            config.contains(shipped),
        )
    }

    /** Gradle runs unit tests from `app/`, but that is not worth relying on. */
    private fun benchConfig(): String? =
        listOf("../tools/llm-bench/bench.json", "tools/llm-bench/bench.json")
            .map(::File)
            .firstOrNull { it.isFile }
            ?.readText()
}
