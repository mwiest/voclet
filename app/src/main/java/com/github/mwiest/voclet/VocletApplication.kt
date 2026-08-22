package com.github.mwiest.voclet

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import android.app.Application

@HiltAndroidApp
class VocletApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase AI Logic requires App Check enforcement. During development we use
        // the Debug provider: on first run it prints a debug token to Logcat (tag
        // "DebugAppCheckProvider") that must be registered under
        // Firebase Console -> App Check -> Apps -> Manage debug tokens.
        // TODO: Before shipping, replace this with the Play Integrity provider.
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
