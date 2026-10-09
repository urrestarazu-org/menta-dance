package com.menta.android.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.menta.android.presentation.home.HomeScreen
import com.menta.android.presentation.login.LoginRoute

@Composable
fun MentaNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Login) {
        composable<Login> {
            LoginRoute(
                onSignedIn = {
                    navController.navigate(Authenticated) {
                        popUpTo<Login> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        navigation<Authenticated>(startDestination = Home) {
            composable<Home> { HomeScreen() }
        }
    }
}
