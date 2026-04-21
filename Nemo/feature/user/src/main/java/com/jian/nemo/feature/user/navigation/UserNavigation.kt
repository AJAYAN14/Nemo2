package com.jian.nemo.feature.user.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.jian.nemo.feature.user.LoginScreen


const val ROUTE_LOGIN = "login"
const val ROUTE_PROFILE = "profile"

fun NavController.navigateToLogin(navOptions: NavOptions? = null) {
    this.navigate(ROUTE_LOGIN, navOptions)
}

fun NavController.navigateToProfile(navOptions: NavOptions? = null) {
    this.navigate(ROUTE_PROFILE, navOptions)
}

fun NavGraphBuilder.userGraph(
    navController: NavController,
    onLoginSuccess: () -> Unit
) {
    composable(ROUTE_LOGIN) {
        LoginScreen(
            onNavigateToRegister = { /* redundant in current UI */ },
            onLoginSuccess = onLoginSuccess
        )
    }
    composable(ROUTE_PROFILE) {
        com.jian.nemo.feature.user.AccountManagementScreen(
            onNavigateBack = {
                navController.popBackStack()
            },

            onLogoutSuccess = {
                // Navigate to Login and clear backstack
                navController.navigate(ROUTE_LOGIN) {
                    popUpTo(0)
                }
            }
        )
    }
}
