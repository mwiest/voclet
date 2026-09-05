package com.github.mwiest.voclet.data.ai.local

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * Runs the shipped translation path against the downloaded text model, on real
 * hardware: the real prompt, the real turn markers, the real cleaner, the real
 * parser.
 *
 * Everything between those four is what has broken before, and always silently.
 * Prompting with ISO codes made the model echo the source word back (`Haus` for
 * `Haus`). The wrong turn markers made it answer with
 * `English:<end_of_utterance>`, which then reached the user as a translation.
 * Neither shows up in a unit test, because both need a real model to happen at
 * all - so this is the one test that can catch them, and it asserts on the
 * parsed suggestion rather than on raw text.
 *
 * Deliberately one test method: `@Before` loads the model, which costs seconds
 * and a lot of memory, so a second method would pay it twice.
 *
 * **Never run this with `connectedDebugAndroidTest`** - it uninstalls the app
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

    @Before
    fun loadModel() {
        // Text only: that is what translation runs on.
        val found = AiModel.TEXT.firstOrNull { File(modelsDir, it.ggufFileName).isFile }
        assumeTrue(
            "No text model in $modelsDir. Download one in Settings; note that " +
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
    }

    @After
    fun releaseModel() {
        contextId?.let { id -> runCatching { llama.releaseContext(id) } }
        openedFds.forEach { runCatching { it.close() } }
        openedFds.clear()
    }

    @Test
    fun theShippedPromptTranslates() {
        var correct = 0
        for ((word, accepted) in WORDS) {
            val raw = complete(LlmPrompts.translation(word, "German", "English"))
            val parsed = LocalTranslationParser.parse(raw)
            val primary = parsed?.primaryTranslation.orEmpty()
            val ok = accepted.any { primary.contains(it, ignoreCase = true) }
            if (ok) correct++
            // Raw as well as parsed: when this fails, the two together say
            // whether the model answered badly or the parsing lost a good answer.
            Log.d(
                TAG,
                "  $word -> raw=<<<$raw>>> primary=<<<$primary>>> alts=${parsed?.alternatives} " +
                    if (ok) "OK" else "MISS (want $accepted)",
            )
        }

        Log.d(TAG, "=== ${model.displayName}: $correct/${WORDS.size}")
        // Every word, because these are the easiest words in the language. One
        // miss means something structural is wrong, not that the model is weak.
        assertTrue(
            "$correct/${WORDS.size} translated. Check the raw answers above: " +
                "the source word echoed back means the prompt, turn markers in " +
                "the answer mean ${model.displayName}'s promptFormat is wrong.",
            correct == WORDS.size,
        )
    }

    /** One completion, with exactly the engine's template, sampling and cleaning. */
    private fun complete(userPrompt: String): String {
        streamed.setLength(0)
        runBlocking {
            llama.launchCompletion(
                contextId!!,
                mapOf(
                    "prompt" to model.promptFormat
                        .replace(AiModel.PROMPT_PLACEHOLDER, userPrompt),
                    "emit_partial_completion" to true,
                    "n_predict" to 48,
                    "temperature" to 0.0,
                    "top_k" to 1,
                    "n_threads" to THREADS,
                    "stop" to CompletionCleaner.STOP_SEQUENCES,
                    "seed" to 0,
                ),
            )
        }
        return CompletionCleaner.clean(streamed.toString())
    }

    private fun loadConfig(gguf: File): Map<String, Any> = mapOf(
        "model" to Uri.fromFile(gguf).toString(),
        "model_fd" to ownedFd(gguf),
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
         * A noun, a verb and an adjective, so a pass cannot be luck. Several
         * answers can be right - "schnell" is both "fast" and "quick" - and
         * scoring only one of them would fail a correct model.
         */
        val WORDS = listOf(
            "Haus" to listOf("house"),
            "laufen" to listOf("run", "walk"),
            "schnell" to listOf("fast", "quick"),
        )
    }
}
