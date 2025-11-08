package com.github.mwiest.voclet.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.mwiest.voclet.ui.home.HomeScreen
import com.github.mwiest.voclet.ui.wordlist.WordListDetailScreen

object Routes {
    const val HOME = "home"
    const val WORD_LIST_DETAIL = "wordlist/{wordListId}"
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
    }
}
