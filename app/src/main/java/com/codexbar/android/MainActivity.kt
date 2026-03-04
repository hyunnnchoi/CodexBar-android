package com.codexbar.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.codexbar.android.feature.dashboard.DashboardScreen
import com.codexbar.android.feature.settings.SettingsScreen
import com.codexbar.android.ui.theme.CodexBarTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CodexBarTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }

                // Android 13+ notification permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

                    LaunchedEffect(permissionState.status.isGranted) {
                        if (!permissionState.status.isGranted) {
                            permissionState.launchPermissionRequest()
                        }
                    }

                    LaunchedEffect(permissionState.status) {
                        if (!permissionState.status.isGranted) {
                            snackbarHostState.showSnackbar(
                                "Notification permission required for background quota updates"
                            )
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
