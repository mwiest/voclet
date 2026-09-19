package com.github.mwiest.voclet.data.ai.local

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Download / readiness status of a single [DownloadBundle]. */
sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus
    /** [progress] is 0f..1f, or null when the download has not reported size yet. */
    data class Downloading(val progress: Float?) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/**
 * Coordinates downloading, storing and deleting on-device LLM model files.
 *
 * Files live in `filesDir/models/`. Downloads run in [ModelDownloadWorker] via
 * WorkManager (foreground service) so they survive process death; this class
 * enqueues/cancels that work and derives per-model [ModelStatus] by combining
 * WorkManager state with on-disk readiness.
 *
 * Readiness is ultimately a fact about the filesystem, so mutations that only
 * touch disk are announced via [revision]: deleting an already-downloaded model
 * leaves its (finished) work untouched, and without that nudge [statuses] would
 * go on reporting the deleted model as [ModelStatus.Ready].
 */
@Singleton
class ModelRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val modelsDir: File = File(context.filesDir, "models")
    private val workManager = WorkManager.getInstance(context)

    /** Bumped whenever this class changes model files on disk. */
    private val revision = MutableStateFlow(0)

    /** Per-model status, reactive to both download progress and disk changes. */
    val statuses: Flow<Map<String, ModelStatus>> = combine(
        combine(
            DownloadCatalog.ALL.map { bundle ->
                workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.workName(bundle.id))
                    .map { infos -> bundle to infos.firstOrNull() }
            },
        ) { entries -> entries.toList() },
        revision,
    ) { entries, _ ->
        // Mapped here rather than per-flow so that a revision bump re-reads disk.
        entries.associate { (bundle, info) -> bundle.id to statusFor(bundle, info) }
    }

    fun isReady(bundle: DownloadBundle): Boolean = ModelDownloader.isReady(bundle, modelsDir)

    /** Where a bundle's file lands once downloaded. */
    fun fileOf(file: BundleFile): File = File(modelsDir, file.fileName)

    fun ggufFile(model: AiModel): File = File(modelsDir, model.ggufFileName)

    /** The downloaded language model, if any. Only one is kept. */
    fun activeModel(): AiModel? = AiModel.ALL.firstOrNull { isReady(it) }

    fun startDownload(bundle: DownloadBundle) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to bundle.id))
            .build()
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.workName(bundle.id),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelDownload(bundle: DownloadBundle) {
        workManager.cancelUniqueWork(ModelDownloadWorker.workName(bundle.id))
        ModelDownloader.cleanupPartials(bundle, modelsDir)
        revision.value++
    }

    fun delete(bundle: DownloadBundle) {
        workManager.cancelUniqueWork(ModelDownloadWorker.workName(bundle.id))
        ModelDownloader.deleteFiles(bundle, modelsDir)
        revision.value++
    }

    private fun statusFor(bundle: DownloadBundle, info: WorkInfo?): ModelStatus {
        // Disk truth wins: a full set of files is Ready regardless of WorkInfo.
        if (isReady(bundle)) return ModelStatus.Ready
        return when (info?.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                ModelStatus.Downloading(null)
            WorkInfo.State.RUNNING -> {
                val pct = info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, -1)
                ModelStatus.Downloading(if (pct < 0) null else pct / 100f)
            }
            WorkInfo.State.FAILED ->
                ModelStatus.Failed(info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed")
            else -> ModelStatus.NotDownloaded
        }
    }
}
