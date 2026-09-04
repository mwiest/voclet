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

        /**
         * Pure RAM → tier mapping: the largest model the device has the RAM for,
         * read off the catalog rather than restated here. Duplicating the
         * thresholds is how they drift — this pair was 6/12 GiB against models
         * that actually need 10/14, so every 8 GB phone was told to run a model
         * that would thrash it.
         *
         * [ModelTier.LOW] is the floor even on a device below its own
         * requirement: something has to be suggested, and the smallest model is
         * the least bad answer. Whether the device can really carry it is a
         * separate question the UI answers per model.
         */
        fun suggestTierForRam(totalRamBytes: Long): ModelTier = AiModel.ALL
            .sortedByDescending { it.minRamBytes }
            .firstOrNull { totalRamBytes >= it.minRamBytes }
            ?.tier
            ?: ModelTier.LOW

        /** True when [totalRamBytes] meets what [model] needs to run well. */
        fun hasRamFor(model: AiModel, totalRamBytes: Long): Boolean =
            totalRamBytes >= model.minRamBytes
    }
}
