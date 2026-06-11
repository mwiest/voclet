package com.github.mwiest.voclet.data.ai.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Download / readiness status of a single [AiModel]. */
sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus
    /** [progress] is 0f..1f, or null when total size is unknown. */
    data class Downloading(val progress: Float?) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/**
 * Manages downloading, storing and deleting on-device LLM model files.
 *
 * Files live in `filesDir/models/`. Each model needs two files (GGUF weights +
 * mmproj vision projector); a model is [ModelStatus.Ready] only when both are
 * present. Downloads run on an app-scoped coroutine so they survive ViewModel
 * recreation and app backgrounding (process-death survival via WorkManager is a
 * separate concern handled in the Settings layer).
 */
class ModelRepository(
    private val modelsDir: File,
    private val downloader: FileDownloader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = mutableMapOf<String, Job>()

    private val _statuses = MutableStateFlow<Map<String, ModelStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ModelStatus>> = _statuses.asStateFlow()

    init {
        refreshStatuses()
    }

    /** Re-scans the models directory and resets statuses to Ready / NotDownloaded. */
    fun refreshStatuses() {
        _statuses.value = AiModel.ALL.associate { model ->
            model.id to if (isReady(model)) ModelStatus.Ready else ModelStatus.NotDownloaded
        }
    }

    /** True when both files for [model] exist on disk. */
    fun isReady(model: AiModel): Boolean =
        ggufFile(model).exists() && mmprojFile(model).exists()

    /** The GGUF weights file for [model] (may not exist yet). */
    fun ggufFile(model: AiModel): File = File(modelsDir, model.ggufFileName)

    /** The mmproj vision projector file for [model] (may not exist yet). */
    fun mmprojFile(model: AiModel): File = File(modelsDir, model.mmprojFileName)

    /** The single model that is fully downloaded and ready, if any. */
    fun activeModel(): AiModel? = AiModel.ALL.firstOrNull { isReady(it) }

    /** Starts (or resumes) downloading [model]. No-op if already in progress. */
    fun startDownload(model: AiModel) {
        if (downloadJobs[model.id]?.isActive == true) return
        setStatus(model.id, ModelStatus.Downloading(0f))
        downloadJobs[model.id] = scope.launch { runDownload(model) }
    }

    /** Cancels an in-flight download of [model] and discards partial files. */
    fun cancelDownload(model: AiModel) {
        downloadJobs.remove(model.id)?.cancel()
        cleanupPartials(model)
        setStatus(model.id, ModelStatus.NotDownloaded)
    }

    /** Deletes both files for [model] from disk (cancelling any download first). */
    fun delete(model: AiModel) {
        downloadJobs.remove(model.id)?.cancel()
        ggufFile(model).delete()
        mmprojFile(model).delete()
        cleanupPartials(model)
        setStatus(model.id, ModelStatus.NotDownloaded)
    }

    private suspend fun runDownload(model: AiModel) {
        modelsDir.mkdirs()
        // Download into temp files, then atomically rename on full success so a
        // partial/aborted download never reads as Ready.
        val ggufTmp = File(modelsDir, model.ggufFileName + PART_SUFFIX)
        val mmprojTmp = File(modelsDir, model.mmprojFileName + PART_SUFFIX)

        // Combined progress is weighted by the catalog's approximate sizes; the
        // mmproj is small relative to the weights, so this is a good estimate.
        val ggufWeight = 0.92f
        try {
            downloader.download(model.ggufUrl, ggufTmp) { done, total ->
                if (total > 0) {
                    setStatus(model.id, ModelStatus.Downloading(ggufWeight * (done.toFloat() / total)))
                }
            }
            downloader.download(model.mmprojUrl, mmprojTmp) { done, total ->
                if (total > 0) {
                    val frac = ggufWeight + (1f - ggufWeight) * (done.toFloat() / total)
                    setStatus(model.id, ModelStatus.Downloading(frac))
                }
            }
            if (!ggufTmp.renameTo(ggufFile(model)) || !mmprojTmp.renameTo(mmprojFile(model))) {
                throw java.io.IOException("Failed to finalise model files")
            }
            setStatus(model.id, ModelStatus.Ready)
        } catch (e: CancellationException) {
            cleanupPartials(model)
            throw e
        } catch (e: Exception) {
            cleanupPartials(model)
            setStatus(model.id, ModelStatus.Failed(e.message ?: "Download failed"))
        } finally {
            downloadJobs.remove(model.id)
        }
    }

    private fun cleanupPartials(model: AiModel) {
        File(modelsDir, model.ggufFileName + PART_SUFFIX).delete()
        File(modelsDir, model.mmprojFileName + PART_SUFFIX).delete()
    }

    private fun setStatus(id: String, status: ModelStatus) {
        _statuses.update { it + (id to status) }
    }

    companion object {
        private const val PART_SUFFIX = ".part"
    }
}
