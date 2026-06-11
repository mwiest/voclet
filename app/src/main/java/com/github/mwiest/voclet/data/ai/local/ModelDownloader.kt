package com.github.mwiest.voclet.data.ai.local

import java.io.File
import java.io.IOException

/**
 * Pure two-file download logic for a model (GGUF weights + mmproj projector).
 *
 * Kept free of Android/WorkManager dependencies so it can be unit-tested with a
 * fake [FileDownloader] and a temp directory. [ModelDownloadWorker] drives this
 * for real downloads; the worker owns the foreground notification & WorkManager
 * progress reporting.
 */
object ModelDownloader {
    const val PART_SUFFIX = ".part"

    /** True when both final files for [model] exist in [modelsDir]. */
    fun isReady(model: AiModel, modelsDir: File): Boolean =
        File(modelsDir, model.ggufFileName).exists() &&
            File(modelsDir, model.mmprojFileName).exists()

    fun cleanupPartials(model: AiModel, modelsDir: File) {
        File(modelsDir, model.ggufFileName + PART_SUFFIX).delete()
        File(modelsDir, model.mmprojFileName + PART_SUFFIX).delete()
    }

    fun deleteFiles(model: AiModel, modelsDir: File) {
        File(modelsDir, model.ggufFileName).delete()
        File(modelsDir, model.mmprojFileName).delete()
        cleanupPartials(model, modelsDir)
    }

    /**
     * Downloads both files into temp `.part` files, then atomically renames them
     * on full success so a partial/aborted download never reads as ready.
     * [onProgress] receives a 0f..1f fraction. Honours coroutine cancellation
     * (cleans up partials and rethrows). Throws on any network/IO failure.
     */
    suspend fun download(
        model: AiModel,
        modelsDir: File,
        downloader: FileDownloader,
        onProgress: (Float) -> Unit,
    ) {
        modelsDir.mkdirs()
        val ggufTmp = File(modelsDir, model.ggufFileName + PART_SUFFIX)
        val mmprojTmp = File(modelsDir, model.mmprojFileName + PART_SUFFIX)

        // Combined progress weighted by approximate sizes: the mmproj projector
        // is small relative to the main weights.
        val ggufWeight = 0.92f
        downloader.download(model.ggufUrl, ggufTmp) { done, total ->
            if (total > 0) onProgress(ggufWeight * (done.toFloat() / total))
        }
        downloader.download(model.mmprojUrl, mmprojTmp) { done, total ->
            if (total > 0) onProgress(ggufWeight + (1f - ggufWeight) * (done.toFloat() / total))
        }
        if (!ggufTmp.renameTo(File(modelsDir, model.ggufFileName)) ||
            !mmprojTmp.renameTo(File(modelsDir, model.mmprojFileName))
        ) {
            throw IOException("Failed to finalise model files")
        }
        onProgress(1f)
    }
}
