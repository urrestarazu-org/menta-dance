package com.menta.android.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.menta.android.presentation.home.HomeScreen
import com.menta.android.presentation.login.LoginRoute
import com.menta.android.presentation.mysessions.MySessionsScreen
import com.menta.android.presentation.session.SessionGate
import com.menta.android.presentation.session.SessionViewModel
import com.menta.android.presentation.splash.SplashScreen

@Composable
fun MentaNavHost(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val gate by sessionViewModel.gate.collectAsStateWithLifecycle()

    // The gate is the single source of truth for which area is on screen.
    LaunchedEffect(gate) {
        when (gate) {
            SessionGate.Splash -> Unit
            is SessionGate.SignedOut -> navController.replaceAll(Login)
            SessionGate.SignedIn -> navController.replaceAll(Authenticated)
        }
    }

    NavHost(navController = navController, startDestination = Splash) {
        composable<Splash> { SplashScreen() }
        composable<Login> {
            LoginRoute(
                notice = (gate as? SessionGate.SignedOut)?.notice,
                onSignedIn = sessionViewModel::onSignedIn,
            )
        }
        navigation<Authenticated>(startDestination = Home) {
            composable<Home> {
                AuthenticatedScaffold(
                    selected = StudentSection.Home,
                    onNavigate = navController::navigateToSection,
                    onLogout = sessionViewModel::onLogout,
                ) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
            composable<MySessions> {
                AuthenticatedScaffold(
                    selected = StudentSection.MySessions,
                    onNavigate = navController::navigateToSection,
                    onLogout = sessionViewModel::onLogout,
                ) { innerPadding ->
                    // No QR destination exists yet, so no "Mostrar QR" button is offered.
                    MySessionsScreen(onShowQr = null, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** Navigates to [destination] and clears the whole back stack, so Back never returns to the previous area. */
private fun NavController.replaceAll(destination: Any) {
    navigate(destination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Drawer navigation: Home stays at the bottom of the stack, so Back from any section returns to it. */
private fun NavController.navigateToSection(section: StudentSection) {
    navigate(section.route) {
        popUpTo(Home)
        launchSingleTop = true
    }
}

private val StudentSection.route: Any
    get() = when (this) {
        StudentSection.Home -> Home
        StudentSection.MySessions -> MySessions
    }
