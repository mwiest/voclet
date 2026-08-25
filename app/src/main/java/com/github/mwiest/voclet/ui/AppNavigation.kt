package com.github.mwiest.voclet.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.mwiest.voclet.ui.home.HomeScreen
import com.github.mwiest.voclet.ui.practice.ConnectPracticeScreen
import com.github.mwiest.voclet.ui.practice.FlashcardPracticeScreen
import com.github.mwiest.voclet.ui.practice.FillBlanksPracticeScreen
import com.github.mwiest.voclet.ui.practice.SpellItPracticeScreen
import com.github.mwiest.voclet.ui.settings.CloudAiSettingsScreen
import com.github.mwiest.voclet.ui.settings.LanguageVariantsScreen
import com.github.mwiest.voclet.ui.settings.OnDeviceAiSettingsScreen
import com.github.mwiest.voclet.ui.settings.SettingsScreen
import com.github.mwiest.voclet.ui.wordlist.WordListDetailScreen

object Routes {
    const val HOME = "home"
    const val WORD_LIST_DETAIL = "wordlist/{wordListId}"
    const val FLASHCARD_PRACTICE = "flashcard_practice/{selectedListIds}/{focusFilter}"
    const val CONNECT_PRACTICE = "connect_practice/{selectedListIds}/{focusFilter}"
    const val FILL_BLANKS_PRACTICE = "fill_blanks_practice/{selectedListIds}/{focusFilter}"
    const val SPELL_IT_PRACTICE = "spell_it_practice/{selectedListIds}/{focusFilter}"
    const val SETTINGS = "settings"
    const val SETTINGS_CLOUD_AI = "settings/cloud_ai"
    const val SETTINGS_ON_DEVICE_AI = "settings/on_device_ai"
    const val SETTINGS_TTS_VARIANTS = "settings/tts_variants"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }
        composable(Routes.WORD_LIST_DETAIL) {
            WordListDetailScreen(navController = navController)
        }
        composable(Routes.FLASHCARD_PRACTICE) {
            FlashcardPracticeScreen(navController = navController)
        }
        composable(Routes.CONNECT_PRACTICE) {
            ConnectPracticeScreen(navController = navController)
        }
        composable(Routes.FILL_BLANKS_PRACTICE) {
            FillBlanksPracticeScreen(navController = navController)
        }
        composable(Routes.SPELL_IT_PRACTICE) {
            SpellItPracticeScreen(navController = navController)
        }
        composable(
            route = "${Routes.SETTINGS}?scrollToAi={scrollToAi}",
            arguments = listOf(
                navArgument("scrollToAi") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            SettingsScreen(
                navController = navController,
                scrollToAi = backStackEntry.arguments?.getBoolean("scrollToAi") == true
            )
        }
        composable(Routes.SETTINGS_CLOUD_AI) {
            CloudAiSettingsScreen(navController = navController)
        }
        composable(Routes.SETTINGS_ON_DEVICE_AI) {
            OnDeviceAiSettingsScreen(navController = navController)
        }
        composable(Routes.SETTINGS_TTS_VARIANTS) {
            LanguageVariantsScreen(navController = navController)
        }
    }
}
