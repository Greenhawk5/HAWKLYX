package ir.alifaniani.hawklyx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ir.alifaniani.hawklyx.data.repository.HawklyxRepository
import ir.alifaniani.hawklyx.ui.screens.AboutScreen
import ir.alifaniani.hawklyx.ui.screens.HomeScreen
import ir.alifaniani.hawklyx.ui.theme.HAWKLYXTheme
import ir.alifaniani.hawklyx.ui.theme.HawklyxDarkBackground
import ir.alifaniani.hawklyx.widget.HawklyxSyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class Screen {
    data object Home : Screen()
    data object About : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: HawklyxRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = HawklyxRepository.getInstance(applicationContext)

        // Schedule periodic WorkManager background synchronization (15-min cadence)
        HawklyxSyncWorker.schedulePeriodicSync(applicationContext)

        setContent {
            HAWKLYXTheme {
                val uiState by repository.uiState.collectAsState()
                val coroutineScope = rememberCoroutineScope()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                // Synchronize on launch (non-forced to leverage edge cache if fresh)
                LaunchedEffect(Unit) {
                    repository.refreshPrice(force = false)
                }

                // Deterministic 1-second countdown ticker for cooldown and scheduled cadence
                // Lifecycle-aware: automatically suspends when activity is stopped or disposed
                LaunchedEffect(Unit) {
                    while (isActive) {
                        repository.updateCountdowns()
                        delay(1000L)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = HawklyxDarkBackground
                ) { innerPadding ->
                    Crossfade(
                        targetState = currentScreen,
                        label = "screen_navigation",
                        modifier = Modifier.padding(innerPadding)
                    ) { screen ->
                        when (screen) {
                            Screen.Home -> {
                                HomeScreen(
                                    uiState = uiState,
                                    onRefresh = {
                                        coroutineScope.launch {
                                            repository.refreshPrice(force = true)
                                        }
                                    },
                                    onNavigateToAbout = {
                                        currentScreen = Screen.About
                                    }
                                )
                            }
                            Screen.About -> {
                                AboutScreen(
                                    onNavigateBack = {
                                        currentScreen = Screen.Home
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}