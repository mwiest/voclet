package com.github.mwiest.voclet

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.ThemeMode
import com.github.mwiest.voclet.data.export.PendingImport
import com.github.mwiest.voclet.ui.AppNavigation
import com.github.mwiest.voclet.ui.theme.VocletTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var repository: VocletRepository

    @Inject
    lateinit var pendingImport: PendingImport

    private var darkTheme by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        handleImportIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set the exit animation for the splash screen
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconView = splashScreenView.iconView
            val exitAnimation = AnimationUtils.loadAnimation(this, R.anim.splash_exit)

            exitAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    splashScreenView.remove()
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })

            iconView.startAnimation(exitAnimation)
        }

        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)

        // Observe theme settings and update the theme accordingly
        lifecycleScope.launch {
            repository.getSettings().collect { settings ->
                darkTheme = when (settings?.themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    else -> {
                        // Check system dark mode setting
                        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        uiMode == Configuration.UI_MODE_NIGHT_YES
                    }
                }
                // Set status bar and navigation bar icons to dark when in light theme
                windowInsetsController.isAppearanceLightStatusBars = !darkTheme
                windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }

        setContent {
            val isDark = darkTheme
            VocletTheme(darkTheme = isDark) {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    /** Picks up a .voclet.json opened from a file manager or shared from another app. */
    private fun handleImportIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.extraStream()
            else -> null
        }
        uri?.let { pendingImport.offer(it) }
    }

    @Suppress("DEPRECATION")
    private fun Intent.extraStream(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
