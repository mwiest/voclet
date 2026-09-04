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
 * Pins down what the llama.cpp binding actually promises, on real hardware.
 *
 * `LlamaLlmEngine` cannot be reasoned about from the binding's sources alone —
 * two of its assumptions turned out to be wrong on device, and both are load
 * bearing, so they are asserted here rather than left to be rediscovered:
 *
 * - the completion result map comes back **empty**, so the per-token callback is
 *   the only source of the generated text (see [streamingIsTheOnlySourceOfText])
 * - SmolVLM's GGUF ships **no chat template** native can apply, so the engine's
 *   hardcoded fallback is what formats every prompt (see [modelShipsNoChatTemplate])
 *
 * Needs the SmolVLM model downloaded in the app under test; skips otherwise, so
 * it is safe to run everywhere even though it cannot run everywhere.
 *
 * **Do not run this with `connectedDebugAndroidTest`.** That task uninstalls the
 * app when it finishes, which deletes `filesDir` — including the ~280 MB model
 * these tests need, leaving every later run to skip and still report success.
 * Install and instrument by hand instead, which leaves the app in place:
 *
 * ```
 * adb logcat -G 32M   # a model load alone can wrap the default buffer
 * ./gradlew.bat :app:installDebug :app:installDebugAndroidTest
 * adb logcat -c
 * adb shell am instrument -w \
 *   -e class com.github.mwiest.voclet.data.ai.local.LlamaNativeContractTest \
 *   com.github.mwiest.voclet.test/androidx.test.runner.AndroidJUnitRunner
 * adb logcat -d | grep LlamaContract
 * ```
 *
 * To restore the model without re-downloading it, given host copies of the two
 * `.gguf` files (`adb push` lands them where the app can read them):
 *
 * ```
 * adb push SmolVLM-256M-Instruct-Q8_0.gguf /data/local/tmp/
 * adb shell "run-as com.github.mwiest.voclet sh -c \
 *   'mkdir -p files/models && cat /data/local/tmp/SmolVLM-256M-Instruct-Q8_0.gguf \
 *    > files/models/SmolVLM-256M-Instruct-Q8_0.gguf'"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class LlamaNativeContractTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var llama: LlamaAndroid
    private var contextId: Int? = null

    /** Filled by the load-time token callback as a completion streams. */
    private var tokens = 0
    private val streamed = StringBuilder()

    /** Descriptors handed to native, closed after the context is released. */
    private val openedFds = mutableListOf<ParcelFileDescriptor>()

    private val gguf: File
        get() = File(context.filesDir, "models/SmolVLM-256M-Instruct-Q8_0.gguf")

    private val mmproj: File
        get() = File(context.filesDir, "models/mmproj-SmolVLM-256M-Instruct-Q8_0.gguf")

    @Before
    fun loadModel() {
        // A skip here is easy to mistake for a pass, so say what to do about it.
        assumeTrue(
            "SmolVLM 256M is not in ${gguf.parent}. Download it in Settings, or " +
                "restore it with the adb recipe in this class's KDoc. Note that " +
                "connectedDebugAndroidTest deletes it by uninstalling the app.",
            gguf.isFile && mmproj.isFile,
        )
        llama = LlamaAndroid(context.contentResolver)

        val started = System.currentTimeMillis()
        val result = llama.startEngine(loadConfig()) { token ->
            tokens++
            streamed.append(token)
        }
        assertNotNull("startEngine returned null - the model did not load", result)

        contextId = (result!!["contextId"] as Number).toInt()
        Log.d(TAG, "loaded in ${System.currentTimeMillis() - started}ms, keys=${result.keys}")
    }

    @After
    fun releaseModel() {
        contextId?.let { id -> runCatching { llama.releaseContext(id) } }
        openedFds.forEach { runCatching { it.close() } }
        openedFds.clear()
    }

    /**
     * The finding that settles whether streaming can be turned off: it cannot.
     * `doCompletion` returns an empty map, so nothing but the token callback
     * ever sees the generated text.
     */
    @Test
    fun streamingIsTheOnlySourceOfText() {
        val id = contextId!!
        val result = runBlocking {
            llama.launchCompletion(id, completionParams(prompt(TRANSLATION_PROMPT)))
        }

        assertNotNull("launchCompletion returned null", result)
        Log.d(TAG, "result keys=${result!!.keys}, streamed $tokens tokens: <<<$streamed>>>")

        assertTrue(
            "the result map gained keys $result - the engine could read text from it now",
            result.isEmpty(),
        )
        assertTrue("nothing streamed - there would be no text at all", streamed.isNotEmpty())
    }

    /**
     * SmolVLM ships no applicable chat template, so `LlamaLlmEngine`'s fallback
     * is not a safety net but the actual code path. If this ever starts passing
     * a template through, the fallback stops being load bearing for this model.
     */
    @Test
    fun modelShipsNoChatTemplate() {
        val formatted = runBlocking {
            llama.getFormattedChat(
                contextId!!,
                listOf(mapOf("role" to "user", "content" to "Hello")),
                "",
            ).firstOrNull()
        }

        Log.d(TAG, "chat template output=<<<$formatted>>>")
        assertTrue(
            "the model now returns a chat template ($formatted) - prefer it over the fallback",
            formatted.isNullOrBlank(),
        )
    }

    /**
     * The fallback template has to actually work, or every answer is the model
     * continuing a document instead of replying. Asserted loosely - a 256M model
     * is allowed to be clumsy, but it has to translate a one-word prompt.
     */
    @Test
    fun fallbackTemplateProducesAnAnswer() {
        val id = contextId!!
        runBlocking { llama.launchCompletion(id, completionParams(prompt(TRANSLATION_PROMPT))) }

        val answer = streamed.toString().trim()
        Log.d(TAG, "translation of \"Haus\" in $tokens tokens: <<<$answer>>>")

        assertTrue("nothing was generated", answer.isNotEmpty())
        assertTrue(
            "expected the translation of \"Haus\", got <<<$answer>>>",
            answer.contains("house", ignoreCase = true),
        )
        // A reply, not an essay: the answer must be shorter than the cap, which
        // means the model emitted a stop token of its own accord.
        assertTrue("hit the token cap instead of stopping", tokens < TRANSLATION_MAX_TOKENS)
    }

    /** The cap is what keeps a request from running to the end of the context. */
    @Test
    fun generationStopsAtTheTokenCap() {
        val id = contextId!!
        val cap = 8
        val params = completionParams(
            prompt = prompt("Count from one to five hundred, in words."),
            maxTokens = cap,
        )

        val started = System.currentTimeMillis()
        runBlocking { llama.launchCompletion(id, params) }
        val elapsed = System.currentTimeMillis() - started

        Log.d(TAG, "cap=$cap produced $tokens tokens in ${elapsed}ms (~${rate(tokens, elapsed)})")
        assertTrue("generated $tokens tokens for a cap of $cap", tokens <= cap)
    }

    /**
     * What the streaming question comes down to: whether asking the binding not
     * to forward tokens buys any inference speed. It does not — the flag is read
     * on the Kotlin side of the callback, so the native upcall happens either
     * way and only the string building is skipped.
     */
    @Test
    fun suppressingPartialsDoesNotSpeedUpInference() {
        val id = contextId!!
        val timings = mutableMapOf<Boolean, MutableList<Long>>()

        // Alternate, and discard each first run: the first completion after a
        // load pays for a cold prompt cache.
        repeat(3) {
            for (emit in listOf(true, false)) {
                tokens = 0
                streamed.setLength(0)
                val params = completionParams(prompt(TRANSLATION_PROMPT), emitPartial = emit)

                val started = System.currentTimeMillis()
                runBlocking { llama.launchCompletion(id, params) }
                val elapsed = System.currentTimeMillis() - started

                timings.getOrPut(emit) { mutableListOf() }.add(elapsed)
                Log.d(TAG, "emit_partial=$emit -> ${elapsed}ms, $tokens tokens forwarded")
            }
        }

        val warmAverages = timings.mapValues { (_, runs) -> runs.drop(1).average() }
        warmAverages.forEach { (emit, average) ->
            Log.d(TAG, "emit_partial=$emit: runs=${timings[emit]} warm-avg=${average.toInt()}ms")
        }

        // Not a performance guarantee, just a guard against the premise
        // changing: if suppression ever became a real speed-up, the engine
        // should reconsider streaming (and find another source for the text).
        val streaming = warmAverages.getValue(true)
        val suppressed = warmAverages.getValue(false)
        assertTrue(
            "suppressing partials is now $streaming -> $suppressed ms, a real saving",
            suppressed > streaming * 0.7,
        )
    }

    private fun loadConfig(): Map<String, Any> = mapOf(
        "model" to Uri.fromFile(gguf).toString(),
        "model_fd" to ownedFd(gguf),
        "mmproj_fd" to ownedFd(mmproj),
        "n_ctx" to 4096,
        "n_batch" to 512,
        "n_threads" to THREADS,
        "n_gpu_layers" to 0,
        "embedding" to false,
        "vocab_only" to false,
        "use_mmap" to true,
        "use_mlock" to false,
    )

    private fun completionParams(
        prompt: String,
        maxTokens: Int = TRANSLATION_MAX_TOKENS,
        emitPartial: Boolean = true,
    ): Map<String, Any> = mapOf(
        "prompt" to prompt,
        "emit_partial_completion" to emitPartial,
        "n_predict" to maxTokens,
        "temperature" to 0.0,
        "top_k" to 1,
        "n_threads" to THREADS,
        "stop" to listOf("<end_of_utterance>", "<end_of_turn>", "<|im_end|>", "\nUser:"),
        "seed" to 0,
    )

    /** Wraps [text] exactly the way the engine's fallback does. */
    private fun prompt(text: String): String {
        tokens = 0
        streamed.setLength(0)
        return "<|im_start|>User: $text<end_of_utterance>\nAssistant:"
    }

    private fun ownedFd(file: File): Int {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        openedFds.add(pfd)
        return pfd.dup().detachFd()
    }

    private fun rate(tokens: Int, elapsedMs: Long): String =
        if (elapsedMs <= 0) "n/a" else "%.1f tok/s".format(tokens * 1000.0 / elapsedMs)

    private companion object {
        const val TAG = "LlamaContract"
        const val THREADS = 4
        const val TRANSLATION_MAX_TOKENS = 32
        const val TRANSLATION_PROMPT =
            "Translate from German to English: \"Haus\"\n" +
                "Answer with the English translation only, or a few comma-separated " +
                "alternatives with the most common first. No sentence, no explanation."
    }
}
