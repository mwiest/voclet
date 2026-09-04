package com.github.mwiest.voclet.data.ai.local

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.github.mwiest.voclet.data.ai.AI_LOG_TAG
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaAndroid
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LlmEngine] backed by llama.cpp, driving [LlamaAndroid] directly.
 *
 * The bundled `LlamaHelper` convenience wrapper is deliberately bypassed: it
 * resolves model paths through `ContentResolver` (so a plain `File.absolutePath`
 * can never open), routes tokens through a shared flow that drops events when no
 * subscriber has attached yet, and exposes no way to set `n_predict` or stop
 * sequences — leaving generation unbounded until the context fills. All three
 * are fatal for this use case, and [LlamaAndroid] is public API, so we own the
 * parameter map instead.
 *
 * The active model is loaded lazily on first use and released on memory
 * pressure. Loads and predictions are serialized: the native side holds one
 * context which refuses concurrent completions.
 */
@Singleton
class LlamaLlmEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
) : LlmEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val llama: LlamaAndroid by lazy { LlamaAndroid(context.contentResolver) }

    /**
     * Guards [loadJob] only. Separate from [loadMutex] because it must never be
     * held across the load itself - that is the whole point of the split.
     */
    private val jobMutex = Mutex()
    private val loadMutex = Mutex()
    private val predictMutex = Mutex()

    /** The currently loaded model and its native context id. */
    private data class Loaded(val modelId: String, val contextId: Int)

    @Volatile
    private var loaded: Loaded? = null

    /** The load in flight, shared by every caller waiting for it. */
    @Volatile
    private var loadJob: Deferred<Int>? = null

    /**
     * Where native token callbacks go. The callback is installed once per
     * context at load time and lives as long as the context, so the in-flight
     * request swaps itself in here — set synchronously *before* the completion
     * starts, which is what makes token delivery race-free.
     */
    @Volatile
    private var tokenSink: ((String) -> Unit)? = null

    /** Guards the "no chat template" notice so it is logged once per load. */
    @Volatile
    private var fallbackTemplateLogged = false

    init {
        // Release the (large) model when the system is under memory pressure.
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) shutdown()
            }

            override fun onLowMemory() = shutdown()
            override fun onConfigurationChanged(newConfig: Configuration) {}
        })
    }

    override fun isModelAvailable(): Boolean = modelRepository.activeModel() != null

    override fun suggestTranslation(word: String, fromLang: String, toLang: String): Flow<String> =
        stream(
            prompt = LlmPrompts.translation(word, fromLang, toLang),
            imageUri = null,
            maxTokens = TRANSLATION_MAX_TOKENS,
            timeoutMs = TRANSLATION_TIMEOUT_MS,
        )

    override fun extractWordPairs(imageUri: Uri, lang1: String?, lang2: String?): Flow<String> =
        stream(
            prompt = LlmPrompts.imageExtraction(lang1, lang2),
            imageUri = imageUri,
            maxTokens = EXTRACTION_MAX_TOKENS,
            timeoutMs = EXTRACTION_TIMEOUT_MS,
        )

    /**
     * Runs one bounded prediction, emitting the accumulated response text as
     * tokens arrive and the complete text last. Empty flow if no model is
     * downloaded; [LlmException] on every other failure.
     *
     * Intermediate emissions are conflated — each one is the whole text so far,
     * so a slow collector can safely miss the middle of the stream without
     * affecting the final value.
     */
    private fun stream(
        prompt: String,
        imageUri: Uri?,
        maxTokens: Int,
        timeoutMs: Long,
    ): Flow<String> = channelFlow {
        val model = modelRepository.activeModel() ?: return@channelFlow
        val contextId = awaitLoaded(model)

        predictMutex.withLock {
            val imageFd = imageUri?.let { openImageFd(it) }
            val accumulated = StringBuilder()
            var result: Map<String, Any>? = null

            tokenSink = { token ->
                accumulated.append(token)
                trySend(accumulated.toString())
            }

            val params = completionParams(
                prompt = formatAsChat(contextId, prompt),
                maxTokens = maxTokens,
                imageFd = imageFd,
            )
            val started = System.currentTimeMillis()
            val prediction = launch(Dispatchers.IO) {
                result = llama.launchCompletion(contextId, params)
            }
            try {
                val finished = withTimeoutOrNull(timeoutMs) { prediction.join() } != null
                if (!finished) {
                    throw LlmException(
                        "On-device inference timed out after ${timeoutMs / 1000}s",
                        LlmException.Kind.TIMEOUT,
                    )
                }
                // The completion result map comes back empty even on success —
                // `null` is the only failure signal, and the generated text
                // arrives solely through the token callback. Confirmed on device
                // by LlamaNativeContractTest, so streaming cannot be turned off.
                if (result == null) throw LlmException("On-device inference failed")

                val text = accumulated.toString()
                if (text.isBlank()) throw LlmException("The model generated no output")
                Log.d(
                    AI_LOG_TAG,
                    "Local completion: ${text.length} chars in " +
                        "${System.currentTimeMillis() - started}ms (cap $maxTokens tokens)",
                )
                send(text)
            } finally {
                tokenSink = null
                // Native completion cannot be interrupted by cancelling the
                // coroutine — it has to be asked to stop, and then waited for,
                // or the next request would find the context busy.
                withContext(NonCancellable) {
                    if (prediction.isActive) {
                        runCatching { llama.stopCompletion(contextId) }
                        prediction.join()
                    }
                }
            }
        }
    }.buffer(Channel.CONFLATED)

    /**
     * Returns the live context for [model], waiting at most [LOAD_TIMEOUT_MS]
     * for a load already under way.
     *
     * A native load cannot be interrupted, and on a memory-pressured device it
     * has been measured at minutes (7.4 in the worst case, against 6-20s
     * unpressured) — so on timeout this abandons the *wait*, not the work. The
     * load keeps running in [engineScope], a retry finds it ready, and the
     * caller gets an error it can act on instead of an unbounded spinner.
     *
     * Concurrent callers share one load: the deferred is the dedup key, and
     * [jobMutex] is only ever held long enough to hand it out.
     */
    private suspend fun awaitLoaded(model: AiModel): Int {
        loaded?.let { if (it.modelId == model.id) return it.contextId }

        val load = jobMutex.withLock {
            loadJob?.takeIf { it.isActive }
                ?: engineScope.async { load(model) }.also { loadJob = it }
        }

        return withTimeoutOrNull(LOAD_TIMEOUT_MS) { load.await() } ?: throw LlmException(
            "${model.displayName} is still loading",
            LlmException.Kind.LOADING,
        )
    }

    /** Loads [model] if it is not already the live context, returning its id. */
    private suspend fun load(model: AiModel): Int = loadMutex.withLock {
        loaded?.let { current ->
            if (current.modelId == model.id) return@withLock current.contextId
            releaseLocked(current)
        }

        val gguf = modelRepository.ggufFile(model)
        val mmproj = modelRepository.mmprojFile(model)
        if (!gguf.isFile) {
            throw LlmException("Model file missing: ${gguf.name}", LlmException.Kind.LOAD_FAILED)
        }

        val started = System.currentTimeMillis()
        val result = withContext(Dispatchers.IO) {
            llama.startEngine(loadConfig(gguf, mmproj)) { token -> tokenSink?.invoke(token) }
        } ?: throw LlmException(
            "Failed to load ${model.displayName}",
            LlmException.Kind.LOAD_FAILED,
        )

        val contextId = (result["contextId"] as? Number)?.toInt() ?: throw LlmException(
            "Model loaded without a context id",
            LlmException.Kind.LOAD_FAILED,
        )
        Log.d(
            AI_LOG_TAG,
            "Loaded ${model.displayName} in ${System.currentTimeMillis() - started}ms " +
                "(ctx $CONTEXT_LENGTH, $THREADS threads)",
        )
        fallbackTemplateLogged = false
        loaded = Loaded(model.id, contextId)
        contextId
    }

    /**
     * Native init parameters. `model` is read back through `ContentResolver` for
     * a GGUF magic-number check, so it must carry a scheme — a bare filesystem
     * path resolves to no content provider and the load fails before it starts.
     */
    private fun loadConfig(gguf: File, mmproj: File): Map<String, Any> {
        val config = mutableMapOf<String, Any>(
            "model" to Uri.fromFile(gguf).toString(),
            "model_fd" to openOwnedFd(gguf),
            "n_ctx" to CONTEXT_LENGTH,
            "n_batch" to N_BATCH,
            "n_threads" to THREADS,
            "n_gpu_layers" to 0,
            "embedding" to false,
            "vocab_only" to false,
            // Paging the weights in on demand beats reading the whole file into
            // memory on a low-RAM device; mlock is not permitted for apps.
            "use_mmap" to true,
            "use_mlock" to false,
        )
        if (mmproj.isFile) {
            config["mmproj_fd"] = openOwnedFd(mmproj)
        } else {
            Log.w(AI_LOG_TAG, "No vision projector at ${mmproj.name}; image extraction unavailable")
        }
        return config
    }

    /** Sampling parameters. Both features want the single most likely answer. */
    private fun completionParams(
        prompt: String,
        maxTokens: Int,
        imageFd: Int?,
    ): Map<String, Any> {
        val params = mutableMapOf<String, Any>(
            "prompt" to prompt,
            "emit_partial_completion" to true,
            // The cap is the whole point: left at the -1 default, an instruct
            // model given a prompt it does not want to end simply generates
            // until the context is full, which is minutes on a phone CPU.
            "n_predict" to maxTokens,
            "temperature" to 0.0,
            "top_k" to 1,
            "n_threads" to THREADS,
            "stop" to STOP_SEQUENCES,
            "seed" to 0,
        )
        imageFd?.let { params["image_fds"] = listOf(it) }
        return params
    }

    /**
     * Wraps [prompt] in the chat template baked into the model's GGUF metadata.
     * Asking native for it keeps this model-agnostic — SmolVLM and Gemma use
     * entirely different turn markers — and falls back to SmolVLM's own shape
     * when the model ships no template.
     *
     * The fallback is not hypothetical: SmolVLM 256M returns nothing here, so it
     * is the live path for the LOW tier. Untemplated, an instruct model treats
     * the prompt as a document to continue and never stops on its own, which is
     * why this is not simply skipped.
     */
    private suspend fun formatAsChat(contextId: Int, prompt: String): String {
        val messages = listOf<Map<String, Any>>(mapOf("role" to "user", "content" to prompt))
        val formatted = runCatching {
            llama.getFormattedChat(contextId, messages, "").firstOrNull()
        }.getOrNull()
        if (!formatted.isNullOrBlank()) return formatted

        // Once per load, not once per request: for this model it is the norm.
        if (!fallbackTemplateLogged) {
            fallbackTemplateLogged = true
            Log.i(AI_LOG_TAG, "Model ships no chat template; using the SmolVLM fallback")
        }
        return "<|im_start|>User: $prompt<end_of_utterance>\nAssistant:"
    }

    /**
     * Opens a read-only descriptor and hands ownership to the native side, which
     * is the contract `initContextWithFd` and `doCompletion` expect — so it must
     * not be closed here.
     */
    private fun openOwnedFd(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).detachFd()

    private fun openImageFd(uri: Uri): Int? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.detachFd()
    }.onFailure { Log.w(AI_LOG_TAG, "Could not open scan image $uri", it) }.getOrNull()

    override fun shutdown() {
        if (loaded == null) return
        engineScope.launch {
            loadMutex.withLock {
                loaded?.let { releaseLocked(it) }
            }
        }
    }

    /** Releases the native context. Caller must hold [loadMutex]. */
    private suspend fun releaseLocked(current: Loaded) {
        predictMutex.withLock {
            runCatching { llama.releaseContext(current.contextId) }
                .onFailure { Log.w(AI_LOG_TAG, "Releasing the model context failed", it) }
        }
        loaded = null
    }

    companion object {
        private const val CONTEXT_LENGTH = 4096
        private const val N_BATCH = 512

        /** Caps on generation, in tokens. */
        private const val TRANSLATION_MAX_TOKENS = 32
        private const val EXTRACTION_MAX_TOKENS = 512

        private const val TRANSLATION_TIMEOUT_MS = 30_000L
        private const val EXTRACTION_TIMEOUT_MS = 90_000L

        /** How long a request waits for a load before giving up on the wait. */
        private const val LOAD_TIMEOUT_MS = 60_000L

        /**
         * End-of-turn markers across the catalog's models (SmolVLM, Gemma), plus
         * the start of a hallucinated next turn. Harmless when a model does not
         * use one.
         */
        private val STOP_SEQUENCES = listOf(
            "<end_of_utterance>",
            "<end_of_turn>",
            "<|im_end|>",
            "\nUser:",
        )

        /**
         * Threads for inference. On a big.LITTLE phone, spreading over every
         * core is slower than staying on the fast ones, so half the cores
         * (bounded) approximates "the big cluster".
         */
        private val THREADS = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
    }
}
