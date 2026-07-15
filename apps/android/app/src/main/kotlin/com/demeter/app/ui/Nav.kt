package com.demeter.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.demeter.app.ui.screens.AccountDetailScreen
import com.demeter.app.ui.screens.AddAccountScreen
import com.demeter.app.ui.screens.ImportScreen
import com.demeter.app.ui.screens.MultiImportScreen
import com.demeter.app.ui.screens.OnboardingScreen
import com.demeter.app.ui.screens.ReminderEditorScreen
import com.demeter.app.ui.screens.SettingsScreen
import com.demeter.app.ui.screens.TodayScreen
import com.demeter.app.ui.screens.WindowEditorScreen

@Composable
fun DemeterNavHost(navController: NavHostController, viewModel: DemeterViewModel) {
    NavHost(
        navController = navController,
        startDestination = if (viewModel.onboarded) "today" else "onboarding",
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onContinueLocally = {
                    viewModel.onboarded = true
                    navController.navigate("today") { popUpTo("onboarding") { inclusive = true } }
                },
            )
        }
        composable("today") {
            TodayScreen(
                viewModel = viewModel,
                onAddAccount = { navController.navigate("addAccount") },
                onOpenAccount = { navController.navigate("account/$it") },
                onOpenSettings = { navController.navigate("settings") },
                onUpdateUsage = { accountId, windowId ->
                    navController.navigate("window/$accountId?windowId=$windowId")
                },
            )
        }
        composable("addAccount") {
            AddAccountScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCreated = { accountId ->
                    navController.navigate("window/$accountId") {
                        popUpTo("today")
                    }
                },
            )
        }
        composable(
            route = "window/{accountId}?windowId={windowId}",
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("windowId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            WindowEditorScreen(
                viewModel = viewModel,
                accountId = entry.arguments?.getString("accountId").orEmpty(),
                windowId = entry.arguments?.getString("windowId"),
                onDone = { accountId ->
                    navController.popBackStack()
                    if (navController.currentBackStackEntry?.destination?.route == "today") {
                        navController.navigate("account/$accountId")
                    }
                },
                onBack = { navController.popBackStack() },
                onOpenMultiImport = { navController.navigate("multiImport") },
            )
        }
        composable("multiImport") {
            MultiImportScreen(
                viewModel = viewModel,
                onSaved = { accountId ->
                    navController.navigate("account/$accountId") {
                        popUpTo("today")
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "account/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) { entry ->
            AccountDetailScreen(
                viewModel = viewModel,
                accountId = entry.arguments?.getString("accountId").orEmpty(),
                onBack = { navController.popBackStack() },
                onUpdateUsage = { accountId, windowId ->
                    navController.navigate("window/$accountId?windowId=${windowId ?: ""}".removeSuffix("?windowId="))
                },
                onAddWindow = { accountId -> navController.navigate("window/$accountId") },
                onEditReminder = { accountId, windowId ->
                    navController.navigate("reminder/$accountId/$windowId")
                },
                onDeleted = { navController.popBackStack("today", inclusive = false) },
            )
        }
        composable(
            route = "reminder/{accountId}/{windowId}",
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("windowId") { type = NavType.StringType },
            ),
        ) { entry ->
            ReminderEditorScreen(
                viewModel = viewModel,
                accountId = entry.arguments?.getString("accountId").orEmpty(),
                windowId = entry.arguments?.getString("windowId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable("import") {
            ImportScreen(
                viewModel = viewModel,
                onSaved = { accountId -> navController.navigate("account/$accountId") },
                onAddAccount = { navController.navigate("addAccount") },
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
