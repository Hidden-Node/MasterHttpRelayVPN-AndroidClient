package com.masterhttprelay.vpn.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.masterhttprelay.vpn.ui.config.ConfigScreen
import com.masterhttprelay.vpn.ui.home.HomeScreen
import com.masterhttprelay.vpn.ui.info.InfoScreen
import com.masterhttprelay.vpn.ui.logs.LogsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Config : Screen("config")
    object Logs : Screen("logs")
    object Info : Screen("info")
}

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
        composable(Screen.Config.route) {
            ConfigScreen()
        }
        composable(Screen.Logs.route) {
            LogsScreen()
        }
        composable(Screen.Info.route) {
            InfoScreen()
        }
    }
}
