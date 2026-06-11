package com.github.mwiest.voclet.data.ai.local

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Download / readiness status of a single [AiModel]. */
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
 */
@Singleton
class ModelRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val modelsDir: File = File(context.filesDir, "models")
    private val workManager = WorkManager.getInstance(context)

    /** Per-model status, reactive to both download progress and disk changes. */
    val statuses: Flow<Map<String, ModelStatus>> = combine(
        AiModel.ALL.map { model ->
            workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.workName(model.id))
                .map { infos -> model.id to statusFor(model, infos.firstOrNull()) }
        },
    ) { entries -> entries.toMap() }

    fun isReady(model: AiModel): Boolean = ModelDownloader.isReady(model, modelsDir)

    fun ggufFile(model: AiModel): File = File(modelsDir, model.ggufFileName)

    fun mmprojFile(model: AiModel): File = File(modelsDir, model.mmprojFileName)

    /** The single model that is fully downloaded and ready, if any. */
    fun activeModel(): AiModel? = AiModel.ALL.firstOrNull { isReady(it) }

    fun startDownload(model: AiModel) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to model.id))
            .build()
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.workName(model.id),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelDownload(model: AiModel) {
        workManager.cancelUniqueWork(ModelDownloadWorker.workName(model.id))
        ModelDownloader.cleanupPartials(model, modelsDir)
    }

    fun delete(model: AiModel) {
        workManager.cancelUniqueWork(ModelDownloadWorker.workName(model.id))
        ModelDownloader.deleteFiles(model, modelsDir)
    }

    private fun statusFor(model: AiModel, info: WorkInfo?): ModelStatus {
        // Disk truth wins: a present file pair is Ready regardless of WorkInfo.
        if (isReady(model)) return ModelStatus.Ready
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
