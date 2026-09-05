package com.github.mwiest.voclet.data.ai.local

import java.io.File
import java.io.IOException

/**
 * Pure download logic for a model: GGUF weights, plus an mmproj projector for
 * the vision models that have one.
 *
 * Kept free of Android/WorkManager dependencies so it can be unit-tested with a
 * fake [FileDownloader] and a temp directory. [ModelDownloadWorker] drives this
 * for real downloads; the worker owns the foreground notification & WorkManager
 * progress reporting.
 */
object ModelDownloader {
    const val PART_SUFFIX = ".part"

    /**
     * True when every final file for [model] exists in [modelsDir] - both files
     * for a vision model, the weights alone for a text one.
     *
     * The projector is checked only when the model declares one. Treating a
     * missing projector as "not ready" regardless would leave every text model
     * permanently un-downloadable.
     */
    fun isReady(model: AiModel, modelsDir: File): Boolean =
        File(modelsDir, model.ggufFileName).exists() &&
            model.mmprojFileName?.let { File(modelsDir, it).exists() } != false

    fun cleanupPartials(model: AiModel, modelsDir: File) {
        File(modelsDir, model.ggufFileName + PART_SUFFIX).delete()
        model.mmprojFileName?.let { File(modelsDir, it + PART_SUFFIX).delete() }
    }

    fun deleteFiles(model: AiModel, modelsDir: File) {
        File(modelsDir, model.ggufFileName).delete()
        model.mmprojFileName?.let { File(modelsDir, it).delete() }
        cleanupPartials(model, modelsDir)
    }

    /**
     * Downloads the model's files into temp `.part` files, then atomically
     * renames them on full success so a partial/aborted download never reads as
     * ready. [onProgress] receives a 0f..1f fraction. Honours coroutine
     * cancellation (cleans up partials and rethrows). Throws on any network/IO
     * failure.
     */
    suspend fun download(
        model: AiModel,
        modelsDir: File,
        downloader: FileDownloader,
        onProgress: (Float) -> Unit,
    ) {
        modelsDir.mkdirs()
        val ggufTmp = File(modelsDir, model.ggufFileName + PART_SUFFIX)

        // Weighted by the files' real sizes. A fixed split misreports every
        // model in the catalog: the projector is 37% of the LOW vision download
        // and 23% of the HIGH one, nowhere near the 8% a hardcoded 0.92 assumed
        // - and for a text model it is the whole download or nothing.
        val ggufWeight = model.ggufProgressWeight
        downloader.download(model.ggufUrl, ggufTmp) { done, total ->
            if (total > 0) onProgress(ggufWeight * (done.toFloat() / total))
        }

        val mmprojTmp = model.mmprojFileName?.let { File(modelsDir, it + PART_SUFFIX) }
        if (mmprojTmp != null && model.mmprojUrl != null) {
            downloader.download(model.mmprojUrl, mmprojTmp) { done, total ->
                if (total > 0) onProgress(ggufWeight + (1f - ggufWeight) * (done.toFloat() / total))
            }
        }

        val finalised = ggufTmp.renameTo(File(modelsDir, model.ggufFileName)) &&
            (mmprojTmp == null || mmprojTmp.renameTo(File(modelsDir, model.mmprojFileName!!)))
        if (!finalised) throw IOException("Failed to finalise model files")
        onProgress(1f)
    }
}
