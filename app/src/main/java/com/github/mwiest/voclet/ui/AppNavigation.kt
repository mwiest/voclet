package com.github.mwiest.voclet.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.mwiest.voclet.ui.home.HomeScreen
import com.github.mwiest.voclet.ui.practice.ConnectPracticeScreen
import com.github.mwiest.voclet.ui.practice.FlashcardPracticeScreen
import com.github.mwiest.voclet.ui.wordlist.WordListDetailScreen

object Routes {
    const val HOME = "home"
    const val WORD_LIST_DETAIL = "wordlist/{wordListId}"
    const val FLASHCARD_PRACTICE = "flashcard_practice/{selectedListIds}/{focusFilter}"
    const val CONNECT_PRACTICE = "connect_practice/{selectedListIds}/{focusFilter}"
    const val PATHWAY_PRACTICE = "pathway_practice/{selectedListIds}/{focusFilter}"
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
    }
}
