package com.atonai.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.atonai.ui.chat.ChatScreen
import com.atonai.ui.code.CodeScreen
import com.atonai.ui.mcp.McpScreen
import com.atonai.ui.nav.Routes
import com.atonai.ui.sessions.SessionsScreen
import com.atonai.ui.settings.PermissionsScreen
import com.atonai.ui.settings.SettingsScreen
import com.atonai.ui.theme.DesignGalleryScreen

/**
 * App shell (M2): one [AppViewModel] shared by every destination and no bottom
 * bar — the Chat top bar opens the session list, exactly like the original
 * (05_UI_MAP.md §1). Each destination owns its own Scaffold, so there is no
 * outer Scaffold to double up the system-bar insets.
 */
@Composable
fun AtonNavHost(
    navController: NavHostController = rememberNavController(),
    vm: AppViewModel = viewModel(),
) {
    NavHost(navController = navController, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) {
            ChatScreen(
                vm = vm,
                onOpenSessions = { navController.navigate(Routes.SESSIONS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onNewSession = { vm.newSession() },
                onOpenCode = { navController.navigate(Routes.CODE) },
                onOpenMcp = { navController.navigate(Routes.MCP) },
            )
        }
        composable(Routes.SESSIONS) {
            SessionsScreen(
                vm = vm,
                onOpenSession = { id ->
                    vm.openSession(id)
                    navController.popBackStack(Routes.CHAT, inclusive = false)
                },
                onNewSession = {
                    vm.newSession()
                    navController.popBackStack(Routes.CHAT, inclusive = false)
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) })
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CODE) { CodeScreen() }
        composable(Routes.MCP) { McpScreen() }
        composable(Routes.GALLERY) { DesignGalleryScreen() }
    }
}