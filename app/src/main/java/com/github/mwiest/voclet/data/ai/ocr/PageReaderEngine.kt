package com.github.mwiest.voclet.data.ai.ocr

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import com.github.mwiest.voclet.data.ai.AI_LOG_TAG
import com.github.mwiest.voclet.data.ai.cloud.ImageScaling
import com.github.mwiest.voclet.data.ai.local.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads word pairs off a photographed page, on device.
 *
 * The whole path is `bitmap -> PageReader.read -> GeometryPairing.pairUp`, with
 * no language model anywhere in it — and so, unlike the cloud path, no title
 * and no detected languages either. Only the pairs.
 *
 * This class exists for the two things around that path: the models are held
 * open between pages, because loading 12.7 MB is the expensive part and reading
 * a page is not, and they are released again under memory pressure rather than
 * pinned for the rest of the session.
 */
@Singleton
class PageReaderEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Serializes reads against each other and against [shutdown]. Closing the
     * models out from under a read would take the native side down with it.
     */
    private val mutex = Mutex()

    @Volatile
    private var reader: PageReader? = null

    init {
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) shutdown()
            }

            override fun onLowMemory() = shutdown()
            override fun onConfigurationChanged(newConfig: Configuration) {}
        })
    }

    /** True once the PP-OCRv5 bundle has been downloaded from Settings. */
    fun isAvailable(): Boolean = modelRepository.isReady(PageReaderModels)

    /**
     * Every word pair [page] yields, top to bottom.
     *
     * Takes a couple of seconds on a dense page, so it belongs off the main
     * thread. [onProgress] is called from that thread, once per line read, and
     * is the only thing the caller hears until the pairs come back.
     */
    suspend fun extractPairs(
        page: Bitmap,
        onProgress: (ReadProgress) -> Unit = {},
    ): List<Pair<String, String>> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val opened = openedReader()
                val scaled = page.cappedToLongEdge()
                val started = System.currentTimeMillis()
                val boxes = try {
                    opened.read(scaled, onProgress)
                } finally {
                    if (scaled !== page) scaled.recycle()
                }
                val pairs = GeometryPairing.pairUp(boxes, wholeCells = true)
                Log.d(
                    AI_LOG_TAG,
                    "OCR read ${boxes.size} lines into ${pairs.size} pairs in " +
                        "${System.currentTimeMillis() - started} ms",
                )
                pairs
            }
        }

    /** Releases the models. Safe to call anytime; the next read reopens them. */
    fun shutdown() {
        if (reader == null) return
        engineScope.launch {
            mutex.withLock {
                reader?.close()
                reader = null
            }
        }
    }

    /** Caller must hold [mutex]. */
    private fun openedReader(): PageReader = reader ?: PageReader.open(
        detectorParam = modelRepository.fileOf(PageReaderModels.detectorParam),
        detectorWeights = modelRepository.fileOf(PageReaderModels.detectorWeights),
        recognizerParam = modelRepository.fileOf(PageReaderModels.recognizerParam),
        recognizerWeights = modelRepository.fileOf(PageReaderModels.recognizerWeights),
        dictionary = readDictionary(),
    ).also { reader = it }

    /**
     * The character list, which ships in `assets` rather than with the weights:
     * the ncnn export carries no class names, and recovering them on device
     * would mean parsing PaddleOCR's YAML. [PageReader] checks it against what
     * the model actually emits, which is what catches the two drifting apart.
     */
    private fun readDictionary(): List<String> =
        context.assets.open(DICTIONARY_ASSET).bufferedReader().use { it.readLines() }
            .dropLastWhile { it.isEmpty() }

    /**
     * The page at no more than [MAX_PAGE_LONG_EDGE_PX], or the page itself.
     *
     * More pixels read *worse*, not better: every engine on the bench returned
     * fewer words at 3000 px than at 1600, and the thresholds downstream are
     * tuned at this size. Raising it is not a way to help a page that reads
     * badly.
     */
    private fun Bitmap.cappedToLongEdge(): Bitmap =
        ImageScaling.targetSize(width, height, MAX_PAGE_LONG_EDGE_PX)
            ?.let { Bitmap.createScaledBitmap(this, it.width, it.height, true) }
            ?: this

    private companion object {
        const val DICTIONARY_ASSET = "ocr/latin_dict.txt"

        /** Longest edge, in pixels, of a page handed to the detector. */
        const val MAX_PAGE_LONG_EDGE_PX = 1600
    }
}
