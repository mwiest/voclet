package com.github.mwiest.voclet.data.ai.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.mwiest.voclet.R
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Downloads a model's files in the background as a foreground service so a
 * multi-GB download survives the app being backgrounded or the process being
 * killed. Reports 0..100 progress via [setProgress] and a progress notification.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val model = AiModel.byId(modelId) ?: return Result.failure()
        val modelsDir = File(applicationContext.filesDir, "models")

        createChannel()
        setForeground(foregroundInfo(model.displayName, 0))

        return try {
            ModelDownloader.download(model, modelsDir, HttpFileDownloader()) { fraction ->
                val pct = (fraction * 100).toInt().coerceIn(0, 100)
                setProgressAsync(workDataOf(KEY_PROGRESS to pct))
                notifyProgress(model.displayName, pct)
            }
            Result.success()
        } catch (e: CancellationException) {
            ModelDownloader.cleanupPartials(model, modelsDir)
            throw e
        } catch (e: Exception) {
            ModelDownloader.cleanupPartials(model, modelsDir)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        }
    }

    private fun foregroundInfo(modelName: String, progress: Int): ForegroundInfo {
        val notification = buildNotification(modelName, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(modelName: String, progress: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.ai_download_notification_title))
            .setContentText(modelName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress <= 0)
            .build()

    private fun notifyProgress(modelName: String, progress: Int) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(NOTIFICATION_ID, buildNotification(modelName, progress))
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS not granted — download continues silently.
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.ai_download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            applicationContext.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "ai_model_download"
        private const val NOTIFICATION_ID = 4711

        fun workName(modelId: String): String = "ai_model_download_$modelId"
    }
}
