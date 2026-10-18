package ir.alifaniani.hawklyx

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ir.alifaniani.hawklyx.data.model.HistoryRange
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
                val historyUiState by repository.historyUiState.collectAsState()
                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
                var lastBackPressTime by remember { mutableLongStateOf(0L) }

                // Case A — User on About screen: pressing Back returns to Home without exiting
                BackHandler(enabled = currentScreen == Screen.About) {
                    currentScreen = Screen.Home
                }

                // Case B — User on Home screen: requires double-press within 2 seconds to exit activity
                BackHandler(enabled = currentScreen == Screen.Home) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime < 2000L) {
                        (context as? Activity)?.finish()
                    } else {
                        lastBackPressTime = currentTime
                        Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }

                // Synchronize on launch (non-forced to leverage edge cache if fresh)
                LaunchedEffect(Unit) {
                    repository.refreshPrice(force = false)
                    repository.loadHistory(HistoryRange.ONE_MONTH, forceRefresh = false)
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
                                    historyUiState = historyUiState,
                                    onRefresh = {
                                        coroutineScope.launch {
                                            repository.refreshPrice(force = true)
                                            repository.loadHistory(historyUiState.selectedRange, forceRefresh = true)
                                        }
                                    },
                                    onSelectRange = { range ->
                                        coroutineScope.launch {
                                            repository.loadHistory(range, forceRefresh = false)
                                        }
                                    },
                                    onNavigateToAbout = {
                                        currentScreen = Screen.About
                                    }
                                )
                            }
                            Screen.About -> {
                                AboutScreen(
                                    observation = uiState.observation,
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