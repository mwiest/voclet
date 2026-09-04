package com.github.mwiest.voclet.data.ai.local

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.nehuatl.llamacpp.LlamaAndroid
import java.io.File

/**
 * Measures what the downloaded model actually answers, so translation prompt
 * wording is chosen from evidence rather than intuition.
 *
 * On-device translation came back in the *source* language — the prompt word
 * echoed plus a variation of it. Two causes looked identical from the outside:
 * the prompt named languages by bare ISO code (`Translate from de to en`,
 * `Answer with the en translation only`), or the chat template was wrong and the
 * model was continuing a document instead of replying. Holding everything else
 * fixed settled it on SmolVLM2 2.2B:
 *
 * - ISO codes scored **0/3**, answering `Haus, Haus`, `laufen, laufen, laufen`,
 *   `schnell, schnellen, schnellen` — the reported bug, reproduced.
 * - The same wording with languages named in English translated correctly, but
 *   rambled: `Haus - English translation: house`, repeated to the token cap.
 *
 * So the codes were the cause, and answer *shape* was the follow-up. Ending the
 * prompt on an empty `English:` slot fixed the rambling, and one clause turned
 * out to cost accuracy outright: inviting "a few comma-separated options" took
 * the same prompt from 3/3 to 1/3, back to answering `Haus` for `Haus`. A
 * one-shot example scored well but echoed the whole example back, so nothing
 * downstream could parse it.
 *
 * The variants below keep the winner honest against those two alternatives.
 * Neither model in the catalog supplies a chat template, so the engine's
 * fallback is what wraps these.
 *
 * Deliberately one test method: `@Before` loads the model, and a 2.2B load costs
 * seconds plus heavy memory pressure, so a second method would double it.
 *
 * **Never run this with `connectedDebugAndroidTest`** — it uninstalls the app
 * afterwards and deletes the model. See [LlamaNativeContractTest] for the
 * `am instrument` recipe and the logcat buffer caveat.
 */
@RunWith(AndroidJUnit4::class)
class TranslationPromptTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val modelsDir = File(context.filesDir, "models")

    private lateinit var llama: LlamaAndroid
    private lateinit var model: AiModel
    private var contextId: Int? = null

    private val streamed = StringBuilder()
    private val openedFds = mutableListOf<ParcelFileDescriptor>()

    /** The first catalog model whose weights and projector are both present. */
    private fun downloadedModel(): AiModel? = AiModel.ALL.firstOrNull {
        File(modelsDir, it.ggufFileName).isFile && File(modelsDir, it.mmprojFileName).isFile
    }

    @Before
    fun loadModel() {
        val found = downloadedModel()
        assumeTrue(
            "No catalog model in $modelsDir. Download one in Settings; note that " +
                "connectedDebugAndroidTest deletes it by uninstalling the app.",
            found != null,
        )
        model = found!!
        llama = LlamaAndroid(context.contentResolver)

        val gguf = File(modelsDir, model.ggufFileName)
        val started = System.currentTimeMillis()
        val result = llama.startEngine(loadConfig(gguf)) { token -> streamed.append(token) }
        assertNotNull("startEngine returned null for ${model.displayName}", result)
        contextId = (result!!["contextId"] as Number).toInt()
        Log.d(TAG, "=== ${model.displayName} loaded in ${System.currentTimeMillis() - started}ms")

        val template = runBlocking {
            llama.getFormattedChat(
                contextId!!,
                listOf(mapOf("role" to "user", "content" to "Hello")),
                "",
            ).firstOrNull()
        }
        Log.d(TAG, "chat template: ${if (template.isNullOrBlank()) "FALLBACK" else "<<<$template>>>"}")
    }

    @After
    fun releaseModel() {
        contextId?.let { id -> runCatching { llama.releaseContext(id) } }
        openedFds.forEach { runCatching { it.close() } }
        openedFds.clear()
    }

    @Test
    fun comparePromptVariants() {
        val variants = listOf<Pair<String, (String) -> String>>(
            // What ships after the fix: production wording, languages named.
            "P production+names" to { w ->
                LlmPrompts.translation(w, "German", "English")
            },
            // Instruction first, content last, an explicit slot for the answer.
            "C terse imperative" to { w ->
                "Translate this German word into English. Reply with only the " +
                    "English word.\nGerman: $w\nEnglish:"
            },
            // One worked example. Small models copy a demonstrated shape far
            // more reliably than they follow a description of one.
            "D one-shot" to { w ->
                "Translate German words into English.\n" +
                    "German: Zeit\nEnglish: time\n" +
                    "German: $w\nEnglish:"
            },
        )

        val scores = mutableMapOf<String, String>()
        for ((label, build) in variants) {
            Log.d(TAG, "--- $label")
            var hits = 0
            var clean = 0
            for ((word, accepted) in WORDS) {
                val answer = complete(build(word))
                val hit = accepted.firstOrNull { answer.contains(it, ignoreCase = true) }
                // "Clean" is the property the parser actually needs: the answer
                // is the translation, not a sentence containing it.
                val tidy = hit != null &&
                    answer.length <= hit.length * 3 &&
                    !answer.contains('\n')
                if (hit != null) hits++
                if (tidy) clean++
                Log.d(
                    TAG,
                    "  $word -> <<<$answer>>> " +
                        (if (hit != null) "OK" else "MISS (want $accepted)") +
                        (if (tidy) " CLEAN" else ""),
                )
            }
            scores[label] = "$hits/${WORDS.size} correct, $clean clean"
            Log.d(TAG, "  ${scores[label]}")
        }

        Log.d(TAG, "=== ${model.displayName}: $scores")
        assertTrue(
            "no variant produced a correct translation - suspect the chat template",
            scores.values.any { !it.startsWith("0/") },
        )
    }

    /** Runs one completion with the engine's own template and sampling. */
    private fun complete(userPrompt: String): String {
        streamed.setLength(0)
        val templated = "<|im_start|>User: $userPrompt<end_of_utterance>\nAssistant:"
        runBlocking {
            llama.launchCompletion(
                contextId!!,
                mapOf(
                    "prompt" to templated,
                    "emit_partial_completion" to true,
                    "n_predict" to 24,
                    "temperature" to 0.0,
                    "top_k" to 1,
                    "n_threads" to THREADS,
                    "stop" to listOf(
                        "<end_of_utterance>", "<end_of_turn>", "<|im_end|>", "\nUser:",
                    ),
                    "seed" to 0,
                ),
            )
        }
        return streamed.toString().trim()
    }

    private fun loadConfig(gguf: File): Map<String, Any> = mapOf(
        "model" to Uri.fromFile(gguf).toString(),
        "model_fd" to ownedFd(gguf),
        "mmproj_fd" to ownedFd(File(modelsDir, model.mmprojFileName)),
        "n_ctx" to 1024,
        "n_batch" to 512,
        "n_threads" to THREADS,
        "n_gpu_layers" to 0,
        "embedding" to false,
        "vocab_only" to false,
        "use_mmap" to true,
        "use_mlock" to false,
    )

    private fun ownedFd(file: File): Int {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        openedFds.add(pfd)
        return pfd.dup().detachFd()
    }

    private companion object {
        const val TAG = "LlamaPrompt"
        const val THREADS = 4

        /**
         * A noun, a verb and an adjective, so a variant cannot win by luck.
         * Several answers can be right - "schnell" is both "fast" and "quick" -
         * and scoring only one of them would fail a correct model.
         */
        val WORDS = listOf(
            "Haus" to listOf("house"),
            "laufen" to listOf("run", "walk"),
            "schnell" to listOf("fast", "quick"),
        )
    }
}
