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

        // Schedule periodic battery-efficient background updates
        HawklyxSyncWorker.schedulePeriodicSync(applicationContext)

        setContent {
            HAWKLYXTheme {
                val uiState by repository.uiState.collectAsState()
                val coroutineScope = rememberCoroutineScope()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                // Automatically attempt fresh sync on launch if no data is present yet
                LaunchedEffect(Unit) {
                    if (uiState.observation == null) {
                        repository.refreshPrice(force = false)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = HawklyxDarkBackground
                ) { innerPadding ->
                    Crossfade(
                        targetState = currentScreen,
                        label = "screen_transition",
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