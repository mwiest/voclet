package com.github.mwiest.voclet.data.ai.local

import java.io.File
import java.io.IOException

/**
 * Pure download logic for a [DownloadBundle]: every file, or none of them.
 *
 * Kept free of Android/WorkManager dependencies so it can be unit-tested with a
 * fake [FileDownloader] and a temp directory. [ModelDownloadWorker] drives this
 * for real downloads; the worker owns the foreground notification & WorkManager
 * progress reporting.
 */
object ModelDownloader {
    const val PART_SUFFIX = ".part"

    /** True when every final file of [bundle] exists in [modelsDir]. */
    fun isReady(bundle: DownloadBundle, modelsDir: File): Boolean =
        bundle.files.all { File(modelsDir, it.fileName).exists() }

    fun cleanupPartials(bundle: DownloadBundle, modelsDir: File) {
        bundle.files.forEach { File(modelsDir, it.fileName + PART_SUFFIX).delete() }
    }

    fun deleteFiles(bundle: DownloadBundle, modelsDir: File) {
        bundle.files.forEach { File(modelsDir, it.fileName).delete() }
        cleanupPartials(bundle, modelsDir)
    }

    /**
     * Downloads every file into a temp `.part`, then renames them all once they
     * have all arrived, so a partial or aborted download never reads as ready.
     * [onProgress] receives a 0f..1f fraction. Honours coroutine cancellation
     * (cleans up partials and rethrows). Throws on any network/IO failure.
     */
    suspend fun download(
        bundle: DownloadBundle,
        modelsDir: File,
        downloader: FileDownloader,
        onProgress: (Float) -> Unit,
    ) {
        modelsDir.mkdirs()

        // Weighted by the files' real sizes rather than by their count: the OCR
        // bundle is two 20 KB parameter files and two multi-megabyte weight
        // files, so counting them equally would show the bar leap to half in an
        // instant and then appear to stall.
        val total = bundle.totalSizeBytes.coerceAtLeast(1L).toFloat()
        var finishedBytes = 0L

        val partials = bundle.files.map { File(modelsDir, it.fileName + PART_SUFFIX) }
        bundle.files.forEachIndexed { index, file ->
            downloader.download(file.url, partials[index]) { done, size ->
                if (size > 0) {
                    onProgress(((finishedBytes + done) / total).coerceIn(0f, 1f))
                }
            }
            finishedBytes += file.sizeBytes
        }

        val finalised = bundle.files.withIndex().all { (index, file) ->
            partials[index].renameTo(File(modelsDir, file.fileName))
        }
        if (!finalised) throw IOException("Failed to finalise model files")
        onProgress(1f)
    }
}
