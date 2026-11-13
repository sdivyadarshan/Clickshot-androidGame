package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ArenaGameScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ClickshotApp()
            }
        }
    }
}

@Composable
fun ClickshotApp(gameViewModel: GameViewModel = viewModel()) {
    val currentScreen by gameViewModel.currentScreen.collectAsState()
    val gameTick by gameViewModel.gameTick.collectAsState()
    val selectedGunType by gameViewModel.selectedGunType.collectAsState()
    val isPaused by gameViewModel.isGamePaused.collectAsState()
    val isLevelsMenuOpen by gameViewModel.isLevelsMenuOpen.collectAsState()
    val isSoundEnabled by gameViewModel.isSoundEnabled.collectAsState()
    val campaignStages by gameViewModel.campaignStages.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E293B))
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.ARENA_GAME, AppScreen.HOME, AppScreen.CAMPAIGN, AppScreen.FRIENDS, AppScreen.PROFILE -> {
                    ArenaGameScreen(
                        engine = gameViewModel.activeEngine,
                        gameTick = gameTick,
                        selectedGunType = selectedGunType,
                        isPaused = isPaused,
                        isLevelsMenuOpen = isLevelsMenuOpen,
                        isSoundEnabled = isSoundEnabled,
                        campaignStages = campaignStages,
                        onInitArena = { w, h -> gameViewModel.initializeGameArena(w, h) },
                        onInitArenaBounds = { l, t, r, b -> gameViewModel.initializeGameArenaBounds(l, t, r, b) },
                        onTapScreen = { gameViewModel.tapToShoot() },
                        onSelectGun = { gunType -> gameViewModel.selectGunType(gunType) },
                        onPauseGame = { gameViewModel.pauseGame() },
                        onResumeGame = { gameViewModel.resumeGame() },
                        onOpenLevelsMenu = { gameViewModel.openLevelsMenu() },
                        onCloseLevelsMenu = { gameViewModel.closeLevelsMenu() },
                        onSelectLevel = { levelNum -> gameViewModel.selectLevel(levelNum) },
                        onToggleSound = { gameViewModel.toggleSound() },
                        onRestartLevel = { gameViewModel.resetCurrentLevel() },
                        onNextLevel = { gameViewModel.nextLevel() },
                        onExitArena = { gameViewModel.resetCurrentLevel() }
                    )
                }
            }
        }
    }
}
