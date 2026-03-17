package com.codexbar.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codexbar.android.core.util.BatteryOptimizationHelper
import com.codexbar.android.core.workmanager.WorkManagerInitializer
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.codexbar.android.feature.dashboard.DashboardScreen
import com.codexbar.android.feature.settings.SettingsScreen
import com.codexbar.android.ui.theme.CodexBarTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val batteryOptLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from the battery optimization dialog,
        // schedule workers regardless of the result
        WorkManagerInitializer.scheduleTokenRefresh(this)
        WorkManagerInitializer.schedulePeriodicRefresh(this)
    }

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

                // Battery optimization exemption
                var showBatteryDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this@MainActivity)) {
                        showBatteryDialog = true
                    }
                }

                if (showBatteryDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showBatteryDialog = false
                        },
                        title = { Text("Background Token Refresh") },
                        text = {
                            Text(
                                "To keep your API tokens up to date in the background, " +
                                    "please exempt this app from battery optimization."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                batteryOptLauncher.launch(
                                    BatteryOptimizationHelper
                                        .requestIgnoreBatteryOptimizationsIntent(this@MainActivity)
                                )
                            }) {
                                Text("Allow")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showBatteryDialog = false
                                // Fallback: open system battery settings
                                try {
                                    batteryOptLauncher.launch(
                                        BatteryOptimizationHelper
                                            .openBatteryOptimizationSettingsIntent()
                                    )
                                } catch (_: Exception) {
                                    // Ignore if settings page is not available
                                }
                            }) {
                                Text("Settings")
                            }
                        }
                    )
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
