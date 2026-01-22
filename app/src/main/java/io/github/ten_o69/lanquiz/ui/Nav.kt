package io.github.ten_o69.lanquiz.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.ten_o69.lanquiz.ui.screens.*
import io.github.ten_o69.lanquiz.vm.QuizViewModel

object Routes {
    const val HOME = "home"
    const val HOST = "host"
    const val JOIN = "join"
    const val LOBBY = "lobby"
    const val GAME = "game"
    const val RESULTS = "results"
    const val SETTINGS = "settings"
}

@Composable
fun AppNav(vm: QuizViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(vm, nav) }
        composable(Routes.HOST) { HostSetupScreen(vm, nav) }
        composable(Routes.JOIN) { JoinScreen(vm, nav) }
        composable(Routes.LOBBY) { LobbyScreen(vm, nav) }
        composable(Routes.GAME) { GameScreen(vm, nav) }
        composable(Routes.RESULTS) { ResultsScreen(vm, nav) }
        composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
    }
}
