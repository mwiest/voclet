package com.github.mwiest.voclet.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the device currently has usable internet, so [AiBackendResolver] can
 * route to the on-device model instead of a cloud request that would only time
 * out.
 *
 * Answers per call rather than observing: it is read once when a request is
 * dispatched, and a stale cached answer would be worse than a fresh one.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService<ConnectivityManager>() ?: return false
        val capabilities = connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
            ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
