package com.github.mwiest.voclet.data.export

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries a .voclet.json URI from an incoming VIEW/SEND intent to the home screen.
 *
 * The activity may receive the intent before the home screen exists, so the URI is held
 * rather than emitted, and consumed once the view model has picked it up.
 */
@Singleton
class PendingImport @Inject constructor() {

    private val _uri = MutableStateFlow<Uri?>(null)
    val uri: StateFlow<Uri?> = _uri.asStateFlow()

    fun offer(uri: Uri) {
        _uri.value = uri
    }

    fun consume() {
        _uri.value = null
    }
}
