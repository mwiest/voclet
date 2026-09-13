package com.github.mwiest.voclet.data.ai.local

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.nehuatl.llamacpp.LlamaAndroid
import java.io.File

/**
 * Probes the image path on real hardware. Kept because its answers depend on the
 * device: re-run it when the phone, the binding or the model changes.
 *
 * **What it established on the OnePlus Nord (AC2003, 7.5 GB, 2026-09-10):**
 *
 * 1. **The prompt does not need `<__media__>`.** The binding appends the marker
 *    itself — `processMedia` logs the prompt with ` <__media__>` on the end —
 *    and tokenization then succeeds (`num_chunks=3`, one of them the image). The
 *    strings in `librnllama.so` had suggested `LlamaLlmEngine` must supply it;
 *    it must not, and does not have to. The image path's plumbing is sound.
 * 2. **A 1200x1600 page costs 2310 image tokens.** With `n_ctx = 4096` that
 *    leaves ~1700 for the answer, and a 71-pair page is about that much JSON —
 *    so a dense page barely fits the context at all, quite apart from
 *    `EXTRACTION_MAX_TOKENS` being 512.
 * 3. **The reader is killed by the OS, not slowed down.** LightOnOCR-1B loaded
 *    in 4.8 s, then during image evaluation free memory fell to 84 MB with ZRAM
 *    at 52% and OxygenOS killed the process (`OplusClearSystemService: Killing
 *    ... o-kill`, signal 9). SmolVLM2 2.2B does not die but thrashes: 19.9 s to
 *    load and no completion in fifteen minutes, the process in state D.
 *    So on a phone this size the limit on a 1 GB+ vision model is RAM during
 *    image encode, and no amount of patience gets past it.
 *
 * Run it the way the recipe in [LlamaNativeContractTest] describes; a plain
 * `connectedDebugAndroidTest` would delete the models this needs. `-e model
 * <substring>` narrows it to one reader, which matters because a model too big
 * for the free RAM takes the whole run's output down with it.
 */
@RunWith(AndroidJUnit4::class)
class VisionPathProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A downloadable vision model plus the turn markers it was trained on. */
    private data class Probe(
        val name: String,
        val gguf: String,
        val mmproj: String,
        /** [MARKER] and the prompt text go in here. */
        val template: String,
    )

    /** Smallest first: this phone has 7.5 GB and the marker question needs no
     *  capable model, only a working projector. */
    private val probes = listOf(
        Probe(
            name = "smolvlm-256m",
            gguf = "SmolVLM-256M-Instruct-Q8_0.gguf",
            mmproj = "mmproj-SmolVLM-256M-Instruct-Q8_0.gguf",
            template = "<|im_start|>User: %s%s<end_of_utterance>\nAssistant:",
        ),
        Probe(
            name = "smolvlm2-500m",
            gguf = "SmolVLM2-500M-Video-Instruct-Q8_0.gguf",
            mmproj = "mmproj-SmolVLM2-500M-Video-Instruct-Q8_0.gguf",
            template = "<|im_start|>User: %s%s<end_of_utterance>\nAssistant:",
        ),
        Probe(
            name = "lightonocr-1b",
            gguf = "LightOnOCR-1B-1025-Q8_0.gguf",
            mmproj = "mmproj-LightOnOCR-1B-1025-Q8_0.gguf",
            // Transcribed from the GGUF's own chat_template: ChatML, and it
            // opens with an empty system turn when none is given.
            template = "<|im_start|>system<|im_end|>\n<|im_start|>user\n%s%s<|im_end|>\n" +
                "<|im_start|>assistant\n",
        ),
        Probe(
            name = "smolvlm2-2.2b",
            gguf = "SmolVLM2-2.2B-Instruct-Q4_K_M.gguf",
            mmproj = "mmproj-SmolVLM2-2.2B-Instruct-Q8_0.gguf",
            template = "<|im_start|>User: %s%s<end_of_utterance>\nAssistant:",
        ),
    )

    @Test
    fun probeMarkerAndSpeed() {
        // `-e model lightonocr` narrows the run. Worth having: a model too big
        // for the free RAM thrashes on page-in for many minutes rather than
        // failing, and takes the whole run's output with it.
        val args = InstrumentationRegistry.getArguments()
        val only = args.getString("model")
        val image = args.getString("image") ?: IMAGE
        val prompt = if (args.getString("task") == "lang") LANGUAGE_PROMPT else TRANSCRIBE_PROMPT
        val present = probes
            .filter { only == null || it.name.contains(only, ignoreCase = true) }
            .filter { File(modelsDir, it.gguf).isFile && File(modelsDir, it.mmproj).isFile }
        assumeTrue(
            "no vision model in $modelsDir - push one with the adb recipe in " +
                "LlamaNativeContractTest's KDoc",
            present.isNotEmpty(),
        )
        Log.i(TAG, "probing ${present.map { it.name }} against $image")

        for (probe in present) {
            val llama = LlamaAndroid(context.contentResolver)
            val opened = mutableListOf<ParcelFileDescriptor>()
            val streamed = StringBuilder()
            var tokens = 0
            var firstTokenAt = 0L
            var startedAt = 0L

            val loadStarted = System.currentTimeMillis()
            val loaded = runCatching {
                llama.startEngine(loadConfig(probe, opened)) { token ->
                    if (tokens == 0) firstTokenAt = System.currentTimeMillis() - startedAt
                    tokens++
                    streamed.append(token)
                }
            }
            val ctx = loaded.getOrNull()?.get("contextId")
            if (ctx == null) {
                Log.w(TAG, "${probe.name}: DID NOT LOAD (${loaded.exceptionOrNull()})")
                opened.forEach { runCatching { it.close() } }
                continue
            }
            val id = (ctx as Number).toInt()
            Log.i(TAG, "${probe.name}: loaded in ${System.currentTimeMillis() - loadStarted}ms")

            for (withMarker in listOf(false, true)) {
                tokens = 0
                firstTokenAt = 0L
                streamed.setLength(0)
                val turn = probe.template.format(if (withMarker) MARKER else "", prompt)

                startedAt = System.currentTimeMillis()
                val result = runCatching {
                    runBlocking { llama.launchCompletion(id, params(turn, imageFd(image))) }
                }
                val elapsed = System.currentTimeMillis() - startedAt

                val answer = streamed.toString().trim().replace('\n', ' ')
                val rate = if (elapsed > firstTokenAt && tokens > 1) {
                    "%.2f tok/s".format(tokens * 1000.0 / (elapsed - firstTokenAt))
                } else "n/a"
                Log.i(
                    TAG,
                    "${probe.name} marker=$withMarker -> " +
                        (if (result.isFailure) "THREW ${result.exceptionOrNull()}" else "ok") +
                        ", ${tokens} tokens in ${elapsed}ms (first at ${firstTokenAt}ms, $rate)",
                )
                Log.i(TAG, "${probe.name} marker=$withMarker answer=<<<${answer.take(400)}>>>")
            }

            runCatching { llama.releaseContext(id) }
            opened.forEach { runCatching { it.close() } }
        }
    }

    private fun loadConfig(probe: Probe, opened: MutableList<ParcelFileDescriptor>): Map<String, Any> =
        mapOf(
            "model" to Uri.fromFile(File(modelsDir, probe.gguf)).toString(),
            "model_fd" to ownedFd(File(modelsDir, probe.gguf), opened),
            "mmproj_fd" to ownedFd(File(modelsDir, probe.mmproj), opened),
            "n_ctx" to CONTEXT,
            "n_batch" to 512,
            "n_threads" to THREADS,
            "n_gpu_layers" to 0,
            "embedding" to false,
            "vocab_only" to false,
            "use_mmap" to true,
            "use_mlock" to false,
        )

    private fun params(prompt: String, imageFd: Int): Map<String, Any> = mapOf(
        "prompt" to prompt,
        "emit_partial_completion" to true,
        "n_predict" to MAX_TOKENS,
        "temperature" to 0.0,
        "top_k" to 1,
        "n_threads" to THREADS,
        "seed" to 0,
        "image_fds" to listOf(imageFd),
    )

    /** A fresh descriptor per completion: native takes ownership of each. */
    private fun imageFd(path: String): Int =
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).detachFd()

    private fun ownedFd(file: File, opened: MutableList<ParcelFileDescriptor>): Int {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        opened.add(pfd)
        return pfd.dup().detachFd()
    }

    private val modelsDir: File get() = File(context.filesDir, "models")

    private companion object {
        const val TAG = "VisionProbe"
        const val THREADS = 4
        /**
         * The app's own `n_ctx`, not a roomier one. The first attempt at this
         * probe used 8192 with the 2.2B model and the phone rebooted mid-run,
         * which is consistent with the catalog's note that 1.59 GiB of weights
         * already exhausts ZRAM on a 7.5 GB device. Measuring a configuration
         * the app cannot afford would not have meant anything anyway.
         */
        const val CONTEXT = 4096

        /** Enough to time generation without paying for a whole page. */
        const val MAX_TOKENS = 64

        /** What native splits the prompt on; see the strings in librnllama.so. */
        const val MARKER = "<__media__>"

        /**
         * The photographed glossary, already 1200x1600 - exactly what the app
         * would send after `MAX_IMAGE_LONG_EDGE_PX`, so the image-encode cost
         * measured here is the real one.
         */
        const val IMAGE = "/data/local/tmp/page.jpg"
        const val TRANSCRIBE_PROMPT = "Transcribe every row of this vocabulary list. " +
            "One row per line, the two columns separated by a tab. No other text."

        /**
         * `-e task lang`. Naming the two languages is the one job a tiny model
         * does well - SmolVLM2 500M is 4/4 on it off device while scoring 0/86
         * at transcription - and it is the job the OCR pipeline cannot bootstrap
         * for itself, since Tesseract needs the language before it can produce
         * any text. Run it against a thumbnail (`-e image /data/local/tmp/
         * page512.jpg`): the answer holds at 512 px, where the image costs a
         * fraction of the tokens that got a reader killed at 1600 px.
         */
        const val LANGUAGE_PROMPT = "What language is the text in the left column of this " +
            "vocabulary list, and what language is the text in the right column? Answer with " +
            "only 'left: <language>, right: <language>'."
    }
}
