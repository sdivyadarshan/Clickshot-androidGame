package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.CampaignStage
import com.example.data.model.FriendEntity
import com.example.data.model.MatchHistoryEntity
import com.example.data.model.UserProfile
import com.example.data.repository.GameRepository
import com.example.game.engine.GameEngine
import com.example.game.engine.GameMode
import com.example.game.engine.GameStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class AppScreen {
    HOME,
    CAMPAIGN,
    ARENA_GAME,
    FRIENDS,
    PROFILE
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(AppDatabase.getDatabase(application))

    // Screen State - Defaults directly to ARENA_GAME for instant playability
    private val _currentScreen = MutableStateFlow(AppScreen.ARENA_GAME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // DB Flows
    val userProfile: StateFlow<UserProfile?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allFriends: StateFlow<List<FriendEntity>> = repository.allFriends
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentMatches: StateFlow<List<MatchHistoryEntity>> = repository.recentMatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val campaignStages: StateFlow<List<CampaignStage>> = repository.campaignStages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Clickshot Physics Game Engine
    var activeEngine: GameEngine? = GameEngine(levelNumber = 1, gameMode = GameMode.SOLO_PLAY)
        private set

    private val _gameTick = MutableStateFlow(0L)
    val gameTick: StateFlow<Long> = _gameTick.asStateFlow()

    private val _isGamePaused = MutableStateFlow(false)
    val isGamePaused: StateFlow<Boolean> = _isGamePaused.asStateFlow()

    private var gameLoopJob: Job? = null
    var currentLevel: Int = 1

    init {
        startGameLoop()
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        if (screen == AppScreen.ARENA_GAME && gameLoopJob == null) {
            startGameLoop()
        }
    }

    // Start / Initialize Clickshot Physics Arena
    fun initializeGameArena(width: Float, height: Float) {
        if (activeEngine == null) {
            activeEngine = GameEngine(levelNumber = currentLevel, gameMode = GameMode.SOLO_PLAY)
        }
        activeEngine?.initializeArena(width, height)
        startGameLoop()
    }

    fun initializeGameArenaBounds(left: Float, top: Float, right: Float, bottom: Float) {
        if (activeEngine == null) {
            activeEngine = GameEngine(levelNumber = currentLevel, gameMode = GameMode.SOLO_PLAY)
        }
        activeEngine?.initializeArenaWithBounds(left, top, right, bottom)
        startGameLoop()
    }

    /**
     * One-Touch Tap Control: Triggers physics recoil shot
     */
    fun tapToShoot() {
        activeEngine?.tapToShoot()
    }

    fun resetCurrentLevel() {
        activeEngine?.resetLevel()
    }

    fun nextLevel() {
        currentLevel++
        val prevLeft = activeEngine?.arenaLeft ?: 40f
        val prevTop = activeEngine?.arenaTop ?: 60f
        val prevRight = activeEngine?.arenaRight ?: 760f
        val prevBottom = activeEngine?.arenaBottom ?: 1140f

        activeEngine = GameEngine(levelNumber = currentLevel, gameMode = GameMode.SOLO_PLAY)
        activeEngine?.initializeArenaWithBounds(prevLeft, prevTop, prevRight, prevBottom)
    }

    fun togglePauseGame() {
        _isGamePaused.value = !_isGamePaused.value
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        _isGamePaused.value = false
        gameLoopJob = viewModelScope.launch {
            var lastTime = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val dt = ((now - lastTime) / 1000f).coerceIn(0.001f, 0.05f)
                lastTime = now

                if (!_isGamePaused.value) {
                    val engine = activeEngine
                    if (engine != null) {
                        engine.update(dt)
                        _gameTick.value = now

                        if (engine.gameStatus == GameStatus.WON && engine.shotsHitCount == 1) {
                            handleLevelCompleted(engine)
                        }
                    }
                }
                delay(16) // ~60 FPS update
            }
        }
    }

    private fun handleLevelCompleted(engine: GameEngine) {
        viewModelScope.launch {
            repository.completeCampaignStage(
                stageNumber = engine.levelNumber,
                score = 1000 + (10 - engine.shotsFiredCount.coerceAtMost(9)) * 100,
                stars = if (engine.shotsFiredCount <= 2) 3 else 2,
                rewardCoins = 100
            )
        }
    }

    fun exitGameArena() {
        gameLoopJob?.cancel()
        _currentScreen.value = AppScreen.HOME
    }

    // Friend Management
    fun addNewFriend(friendId: String, friendName: String) {
        viewModelScope.launch {
            repository.addFriend(
                FriendEntity(
                    friendId = friendId.trim().uppercase(),
                    displayName = friendName.ifBlank { "Player" },
                    avatarIndex = Random.nextInt(0, 6),
                    rating = Random.nextInt(1150, 1500),
                    isOnline = true,
                    inMatch = false,
                    statusMessage = "Online"
                )
            )
        }
    }

    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            repository.removeFriend(friendId)
        }
    }

    fun updateProfileNameAndAvatar(newName: String, avatarIdx: Int) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfile()
            repository.saveUserProfile(
                current.copy(
                    displayName = newName.ifBlank { current.displayName },
                    avatarIndex = avatarIdx
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
    }
}
