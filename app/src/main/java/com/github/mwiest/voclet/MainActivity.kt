package com.github.mwiest.voclet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.github.mwiest.voclet.ui.AppNavigation
import com.github.mwiest.voclet.ui.theme.VocletTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VocletTheme {
                AppNavigation()
            }
        }
    }
}
