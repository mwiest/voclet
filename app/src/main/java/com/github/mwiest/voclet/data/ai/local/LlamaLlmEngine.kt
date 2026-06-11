package com.github.mwiest.voclet.data.ai.local

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaHelper
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [LlmEngine] backed by llama.cpp (kotlin-llama-cpp). The active model is loaded
 * lazily on first use and released on memory pressure. Loads and predictions are
 * serialized — the underlying helper holds a single model and emits all tokens
 * onto one shared event flow.
 */
@Singleton
class LlamaLlmEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
) : LlmEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val events = MutableSharedFlow<LlamaHelper.LLMEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val helper: LlamaHelper by lazy {
        LlamaHelper(context.contentResolver, engineScope, events)
    }

    private val loadMutex = Mutex()
    private val predictMutex = Mutex()

    @Volatile
    private var loadedModelId: String? = null

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
        stream(LlmPrompts.translation(word, fromLang, toLang), imagePath = null)

    override fun extractWordPairs(imageUri: Uri, lang1: String?, lang2: String?): Flow<String> =
        stream(LlmPrompts.imageExtraction(lang1, lang2), imagePath = imageUri.toString())

    /**
     * Runs one prediction, emitting the accumulated response text on each token
     * and completing on the model's Done event. Empty flow if no model is ready.
     */
    private fun stream(prompt: String, imagePath: String?): Flow<String> = channelFlow {
        val model = modelRepository.activeModel() ?: return@channelFlow
        ensureLoaded(model)

        // Serialize: the helper streams every prediction onto the same `events`
        // flow, so only one may run at a time.
        predictMutex.withLock {
            val builder = StringBuilder()
            val collector = launch {
                events.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Ongoing -> {
                            builder.append(event.word)
                            trySend(builder.toString())
                        }
                        is LlamaHelper.LLMEvent.Done -> close()
                        is LlamaHelper.LLMEvent.Error -> close(LlmException(event.message))
                        else -> Unit
                    }
                }
            }
            helper.predict(prompt = prompt, imagePath = imagePath)
            awaitClose {
                collector.cancel()
                helper.stopPrediction()
            }
        }
    }

    private suspend fun ensureLoaded(model: AiModel) = loadMutex.withLock {
        if (loadedModelId == model.id) return@withLock
        if (loadedModelId != null) {
            helper.release()
            loadedModelId = null
        }
        val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                helper.load(
                    path = modelRepository.ggufFile(model).absolutePath,
                    contextLength = CONTEXT_LENGTH,
                    mmprojPath = modelRepository.mmprojFile(model).absolutePath,
                ) { if (cont.isActive) cont.resume(Unit) }
            }
            true
        }
        if (loaded == null) {
            helper.abort()
            helper.release()
            throw LlmException("Model load timed out")
        }
        loadedModelId = model.id
    }

    override fun shutdown() {
        if (loadedModelId == null) return
        engineScope.launch {
            loadMutex.withLock {
                if (loadedModelId != null) {
                    helper.abort()
                    helper.release()
                    loadedModelId = null
                }
            }
        }
    }

    companion object {
        private const val CONTEXT_LENGTH = 4096
        private const val LOAD_TIMEOUT_MS = 120_000L
    }
}
