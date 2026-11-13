package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.MultiplayerRepository
import com.example.data.remote.OperationType
import com.example.data.remote.PvpRoom
import com.example.data.remote.RemotePlayerState
import com.example.data.remote.RemoteUserProfile
import com.example.data.remote.handleFirestoreError
import com.example.data.remote.unwrapFirestoreException
import com.example.game.audio.GameSoundEvent
import com.example.game.audio.SoundEffectsManager
import com.example.game.engine.GameEngine
import com.example.game.engine.GameMode
import com.example.game.engine.GameStatus
import com.example.game.engine.GunType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface MultiplayerMatchStage {
    data object Hub : MultiplayerMatchStage
    data object Connecting : MultiplayerMatchStage
    data class WaitingForOpponent(val room: PvpRoom) : MultiplayerMatchStage
    data class ActiveMatch(val room: PvpRoom, val opponentName: String) : MultiplayerMatchStage
    data class MatchFinished(val room: PvpRoom, val didWin: Boolean, val opponentName: String) : MultiplayerMatchStage
}

class MultiplayerViewModel(
    private val repository: MultiplayerRepository,
    val currentUserId: String,
    val playerDisplayName: String,
    private val soundManager: SoundEffectsManager
) : ViewModel() {

    private val _stage = MutableStateFlow<MultiplayerMatchStage>(MultiplayerMatchStage.Hub)
    val stage: StateFlow<MultiplayerMatchStage> = _stage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _onlineProfile = MutableStateFlow<RemoteUserProfile?>(null)
    val onlineProfile: StateFlow<RemoteUserProfile?> = _onlineProfile.asStateFlow()

    private val _selectedGunType = MutableStateFlow(GunType.PULSE_PISTOL)
    val selectedGunType: StateFlow<GunType> = _selectedGunType.asStateFlow()

    private val _gameTick = MutableStateFlow(0L)
    val gameTick: StateFlow<Long> = _gameTick.asStateFlow()

    private val _opponentState = MutableStateFlow<RemotePlayerState?>(null)
    val opponentState: StateFlow<RemotePlayerState?> = _opponentState.asStateFlow()

    var pvpEngine: GameEngine? = null
        private set

    private var roomJob: Job? = null
    private var opponentJob: Job? = null
    private var shotsJob: Job? = null
    private var loopJob: Job? = null
    private var syncJob: Job? = null

    private var activeRoomId: String? = null
    private var activeOpponentUid: String? = null
    private var shotSeq: Long = 0L
    private val processedShotIds = HashSet<String>()
    private var lastBounds: FloatArray? = null
    private var finishReported = false

    init {
        loadOrCreateProfile()
    }

    private fun loadOrCreateProfile() {
        viewModelScope.launch {
            repository.getOrCreateUserProfile(currentUserId, playerDisplayName)
                .onSuccess { _onlineProfile.value = it }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to load online profile" }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun selectGunType(type: GunType) {
        _selectedGunType.value = type
        pvpEngine?.setGunType(type)
        soundManager.play(GameSoundEvent.UI_CLICK)
        _gameTick.value = System.currentTimeMillis()
        activeRoomId?.let { roomId ->
            viewModelScope.launch { publishCurrentPose(roomId) }
        }
    }

    fun startQuickMatch() {
        clearError()
        stopMatchJobs()
        _stage.value = MultiplayerMatchStage.Connecting
        soundManager.play(GameSoundEvent.UI_CLICK)

        viewModelScope.launch {
            val result = repository.findQuickMatchOrCreate(
                uid = currentUserId,
                displayName = playerDisplayName,
                gunType = _selectedGunType.value
            )
            result.onSuccess { room ->
                enterRoom(room)
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Quick Match connection failed."
                _stage.value = MultiplayerMatchStage.Hub
            }
        }
    }

    fun createPrivateRoom() {
        clearError()
        stopMatchJobs()
        _stage.value = MultiplayerMatchStage.Connecting
        soundManager.play(GameSoundEvent.UI_CLICK)

        viewModelScope.launch {
            val result = repository.createRoom(
                hostUid = currentUserId,
                hostName = playerDisplayName,
                gunType = _selectedGunType.value,
                isPublic = false
            )
            result.onSuccess { room ->
                enterRoom(room)
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Failed to create room."
                _stage.value = MultiplayerMatchStage.Hub
            }
        }
    }

    fun joinRoomByCode(roomCode: String) {
        clearError()
        stopMatchJobs()
        _stage.value = MultiplayerMatchStage.Connecting
        soundManager.play(GameSoundEvent.UI_CLICK)

        viewModelScope.launch {
            val result = repository.joinRoomByCode(
                roomCode = roomCode,
                guestUid = currentUserId,
                guestName = playerDisplayName,
                gunType = _selectedGunType.value
            )
            result.onSuccess { room ->
                enterRoom(room)
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Unable to join room $roomCode."
                _stage.value = MultiplayerMatchStage.Hub
            }
        }
    }

    private fun enterRoom(initialRoom: PvpRoom) {
        activeRoomId = initialRoom.roomCode
        activeOpponentUid = null
        shotSeq = 0L
        processedShotIds.clear()
        finishReported = false

        val isHost = (initialRoom.hostUid == currentUserId)
        val engine = GameEngine(
            levelNumber = 1,
            gameMode = GameMode.DUEL_1V1,
            initialGunType = _selectedGunType.value
        ).apply {
            authoritativeLocalPvp = false
            isOnlineHost = isHost
            onSoundEvent = { soundManager.play(it) }
            onLocalShotFired = { normX, normY, dirAngle, gunType, pellets, spread ->
                onLocalShotTriggered(initialRoom.roomCode, normX, normY, dirAngle, gunType, pellets, spread)
            }
            onLocalHitStateChanged = { hits, eliminated ->
                onLocalHitStateUpdated(initialRoom.roomCode, hits, eliminated)
            }
        }
        lastBounds?.let { b ->
            engine.initializeArenaWithBounds(b[0], b[1], b[2], b[3])
        }
        pvpEngine = engine

        if (initialRoom.status == "waiting") {
            _stage.value = MultiplayerMatchStage.WaitingForOpponent(initialRoom)
        } else if (initialRoom.status == "playing") {
            val oppName = initialRoom.opponentNameFor(currentUserId) ?: "Rival"
            _stage.value = MultiplayerMatchStage.ActiveMatch(initialRoom, oppName)
            attachBattleStreams(initialRoom)
        }

        roomJob = viewModelScope.launch {
            repository.observeRoom(initialRoom.roomCode)
                .catch { error ->
                    val fsErr = error.unwrapFirestoreException()
                    if (fsErr == null && error is CancellationException) throw error
                    handleFirestoreError(error, OperationType.GET, "pvp_rooms/${initialRoom.roomCode}")
                    _errorMessage.value = fsErr?.localizedMessage ?: "Room connection lost."
                }
                .collect { room ->
                    if (room == null) {
                        stopMatchJobs()
                        _stage.value = MultiplayerMatchStage.Hub
                        return@collect
                    }
                    when (room.status) {
                        "waiting" -> {
                            _stage.value = MultiplayerMatchStage.WaitingForOpponent(room)
                        }
                        "playing" -> {
                            val oppName = room.opponentNameFor(currentUserId) ?: "Rival"
                            _stage.value = MultiplayerMatchStage.ActiveMatch(room, oppName)
                            attachBattleStreams(room)
                        }
                        "finished" -> {
                            val didWin = (room.winnerUid == currentUserId)
                            val oppName = room.opponentNameFor(currentUserId) ?: "Rival"
                            if (didWin) {
                                pvpEngine?.markOpponentEliminatedFromRoomWinner()
                            }
                            if (!finishReported) {
                                finishReported = true
                                launch {
                                    repository.recordMatchOutcome(currentUserId, playerDisplayName, didWin)
                                        .onSuccess { loadOrCreateProfile() }
                                }
                            }
                            loopJob?.cancel()
                            syncJob?.cancel()
                            _stage.value = MultiplayerMatchStage.MatchFinished(room, didWin, oppName)
                        }
                    }
                }
        }
    }

    private fun attachBattleStreams(room: PvpRoom) {
        val oppUid = room.opponentUidFor(currentUserId) ?: return
        if (activeOpponentUid == oppUid && opponentJob?.isActive == true) return
        activeOpponentUid = oppUid

        opponentJob?.cancel()
        opponentJob = viewModelScope.launch {
            repository.observeOpponentPlayer(room.roomCode, oppUid)
                .catch { error ->
                    val fsErr = error.unwrapFirestoreException()
                    if (fsErr == null && error is CancellationException) throw error
                    handleFirestoreError(error, OperationType.GET, "pvp_rooms/${room.roomCode}/players/$oppUid")
                }
                .collect { opp ->
                    _opponentState.value = opp
                    if (opp != null) {
                        val gunType = runCatching { GunType.valueOf(opp.gunType) }
                            .getOrDefault(GunType.PULSE_PISTOL)
                        pvpEngine?.applyRemoteOpponentState(
                            normX = opp.normX.toFloat(),
                            normY = opp.normY.toFloat(),
                            normVx = opp.normVx.toFloat(),
                            normVy = opp.normVy.toFloat(),
                            angle = opp.angle.toFloat(),
                            angularVelocity = opp.angularVelocity.toFloat(),
                            gunType = gunType,
                            simultaneousHits = opp.simultaneousHits.toInt(),
                            isEliminated = opp.isEliminated
                        )
                        if (opp.isEliminated && !finishReported) {
                            finishReported = true
                            repository.finishRoom(room.roomCode, winnerUid = currentUserId)
                        }
                    }
                }
        }

        shotsJob?.cancel()
        shotsJob = viewModelScope.launch {
            repository.observeShots(room.roomCode, currentUserId)
                .catch { error ->
                    val fsErr = error.unwrapFirestoreException()
                    if (fsErr == null && error is CancellationException) throw error
                    handleFirestoreError(error, OperationType.LIST, "pvp_rooms/${room.roomCode}/shots")
                }
                .collect { shots ->
                    shots.forEach { shot ->
                        if (shot.id.isNotBlank() && processedShotIds.add(shot.id)) {
                            if (shot.shooterUid != currentUserId) {
                                val gunType = runCatching { GunType.valueOf(shot.gunType) }
                                    .getOrDefault(GunType.PULSE_PISTOL)
                                pvpEngine?.spawnRemoteShot(
                                    normX = shot.normX.toFloat(),
                                    normY = shot.normY.toFloat(),
                                    dirAngle = shot.dirAngle.toFloat(),
                                    gunType = gunType,
                                    pellets = shot.pellets.toInt(),
                                    spreadRadians = shot.spreadRadians.toFloat()
                                )
                            }
                        }
                    }
                }
        }

        startBattleLoops(room.roomCode)
    }

    private fun startBattleLoops(roomId: String) {
        if (loopJob?.isActive != true) {
            loopJob = viewModelScope.launch {
                var lastTime = System.currentTimeMillis()
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val dt = ((now - lastTime) / 1000f).coerceIn(0.001f, 0.05f)
                    lastTime = now
                    val engine = pvpEngine
                    if (engine != null && engine.isInitialized) {
                        engine.update(dt)
                        _gameTick.value = now
                    }
                    delay(16)
                }
            }
        }

        if (syncJob?.isActive != true) {
            syncJob = viewModelScope.launch {
                while (isActive) {
                    delay(90)
                    publishCurrentPose(roomId)
                }
            }
        }
    }

    fun initializeArenaBounds(left: Float, top: Float, right: Float, bottom: Float) {
        lastBounds = floatArrayOf(left, top, right, bottom)
        val engine = pvpEngine ?: return
        val changed = !engine.isInitialized ||
            kotlin.math.abs(engine.arenaLeft - left) > 2f ||
            kotlin.math.abs(engine.arenaRight - right) > 2f ||
            kotlin.math.abs(engine.arenaTop - top) > 2f ||
            kotlin.math.abs(engine.arenaBottom - bottom) > 2f
        if (changed) {
            engine.initializeArenaWithBounds(left, top, right, bottom)
        }
    }

    fun tapToShoot() {
        pvpEngine?.tapToShoot()
    }

    private fun onLocalShotTriggered(
        roomId: String,
        normX: Float,
        normY: Float,
        dirAngle: Float,
        gunType: GunType,
        pellets: Int,
        spread: Float
    ) {
        val oppUid = activeOpponentUid ?: return
        val nextSeq = ++shotSeq
        viewModelScope.launch {
            repository.publishShot(
                roomId = roomId,
                shooterUid = currentUserId,
                opponentUid = oppUid,
                gunType = gunType,
                normX = normX,
                normY = normY,
                dirAngle = dirAngle,
                pellets = pellets,
                spreadRadians = spread,
                seq = nextSeq
            )
            publishCurrentPose(roomId)
        }
    }

    private fun onLocalHitStateUpdated(roomId: String, hits: Int, eliminated: Boolean) {
        viewModelScope.launch {
            publishCurrentPose(roomId)
            if (eliminated && !finishReported) {
                val oppUid = activeOpponentUid
                if (oppUid != null) {
                    finishReported = true
                    repository.finishRoom(roomId, winnerUid = oppUid)
                }
            }
        }
    }

    private suspend fun publishCurrentPose(roomId: String) {
        val engine = pvpEngine ?: return
        if (!engine.isInitialized) return
        val gun = engine.playerGun
        repository.publishPlayerState(
            roomId = roomId,
            uid = currentUserId,
            displayName = playerDisplayName,
            gunType = gun.gunType,
            normX = engine.normalizeX(gun.x),
            normY = engine.normalizeY(gun.y),
            normVx = engine.normalizeVx(gun.vx),
            normVy = engine.normalizeVy(gun.vy),
            angle = gun.angle,
            angularVelocity = gun.angularVelocity,
            shotsFired = gun.shotsFired,
            simultaneousHits = gun.simultaneousHits,
            isEliminated = gun.isDestroyed && gun.simultaneousHits >= engine.killsRequiredHits
        )
    }

    fun leaveOrCancelMatch() {
        val currentStage = _stage.value
        if (currentStage is MultiplayerMatchStage.WaitingForOpponent &&
            currentStage.room.hostUid == currentUserId
        ) {
            viewModelScope.launch {
                repository.cancelWaitingRoom(currentStage.room.roomCode)
            }
        }
        stopMatchJobs()
        _stage.value = MultiplayerMatchStage.Hub
        soundManager.play(GameSoundEvent.UI_CLICK)
    }

    private fun stopMatchJobs() {
        roomJob?.cancel()
        opponentJob?.cancel()
        shotsJob?.cancel()
        loopJob?.cancel()
        syncJob?.cancel()
        roomJob = null
        opponentJob = null
        shotsJob = null
        loopJob = null
        syncJob = null
        activeRoomId = null
        activeOpponentUid = null
        _opponentState.value = null
        pvpEngine = null
    }

    override fun onCleared() {
        super.onCleared()
        stopMatchJobs()
    }

    companion object {
        private const val TAG = "MultiplayerVM"
    }
}
