package com.github.mwiest.voclet.data.ai.local

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads device hardware characteristics to recommend an AI model tier.
 *
 * The recommendation is advisory only — the user may pick any tier in Settings.
 */
@Singleton
class DeviceHardware @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Total physical RAM in bytes, or 0 if it cannot be determined. */
    fun totalRamBytes(): Long {
        val activityManager = context.getSystemService<ActivityManager>() ?: return 0L
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem
    }

    /** The model tier recommended for this device, based on total RAM. */
    fun suggestedTier(): ModelTier = suggestTierForRam(totalRamBytes())

    companion object {
        private const val GIB = 1024L * 1024L * 1024L

        /**
         * Pure RAM → tier mapping (extracted for testability):
         * - ≥ 12 GB → HIGH
         * - ≥ 6 GB  → MID
         * - otherwise → LOW
         */
        fun suggestTierForRam(totalRamBytes: Long): ModelTier = when {
            totalRamBytes >= 12 * GIB -> ModelTier.HIGH
            totalRamBytes >= 6 * GIB -> ModelTier.MID
            else -> ModelTier.LOW
        }
    }
}
