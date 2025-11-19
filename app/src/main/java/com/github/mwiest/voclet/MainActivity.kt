package com.github.mwiest.voclet

import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.github.mwiest.voclet.ui.AppNavigation
import com.github.mwiest.voclet.ui.theme.VocletTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val splashScreen = installSplashScreen()

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

        setContent {
            VocletTheme {
                AppNavigation()
            }
        }
    }
}
