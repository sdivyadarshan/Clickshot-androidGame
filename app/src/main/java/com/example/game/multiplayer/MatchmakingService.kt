package com.example.game.multiplayer

import com.example.game.engine.ArenaTheme
import com.example.game.engine.GameMode
import com.example.game.engine.Team
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class MatchmakingService {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var searchJob: Job? = null

    private val _matchStatus = MutableStateFlow(MatchmakingStatus.IDLE)
    val matchStatus: StateFlow<MatchmakingStatus> = _matchStatus.asStateFlow()

    private val _searchTimeSec = MutableStateFlow(0)
    val searchTimeSec: StateFlow<Int> = _searchTimeSec.asStateFlow()

    private val _matchedGame = MutableStateFlow<MatchFoundInfo?>(null)
    val matchedGame: StateFlow<MatchFoundInfo?> = _matchedGame.asStateFlow()

    private val _currentLobby = MutableStateFlow<LobbyRoom?>(null)
    val currentLobby: StateFlow<LobbyRoom?> = _currentLobby.asStateFlow()

    private val _lobbyMessages = MutableStateFlow<List<LobbyChatMessage>>(emptyList())
    val lobbyMessages: StateFlow<List<LobbyChatMessage>> = _lobbyMessages.asStateFlow()

    private val _incomingInvite = MutableStateFlow<FriendInvite?>(null)
    val incomingInvite: StateFlow<FriendInvite?> = _incomingInvite.asStateFlow()

    private val simulatedRivals = listOf(
        "VortexKnight" to 1280,
        "ShadowReaper" to 1340,
        "NovaPulse" to 1210,
        "CyberNinja" to 1450,
        "PlasmaEcho" to 1190,
        "QuantumStriker" to 1390,
        "SolarFlare" to 1500,
        "GhostProtocol" to 1270,
        "NeonTitan" to 1320,
        "Hyperion99" to 1410
    )

    fun startMatchmaking(mode: GameMode, playerRating: Int, playerName: String) {
        searchJob?.cancel()
        _matchStatus.value = MatchmakingStatus.SEARCHING
        _searchTimeSec.value = 0
        _matchedGame.value = null

        searchJob = scope.launch {
            val searchDuration = Random.nextInt(3, 7)
            for (sec in 1..searchDuration) {
                delay(1000)
                _searchTimeSec.value = sec
            }

            // Generate realistic matched opponents and teammates
            val available = simulatedRivals.shuffled()
            val opp1 = available[0]
            val opp2 = available[1]
            val ally = available[2]

            val matchInfo = MatchFoundInfo(
                matchId = "MATCH-${Random.nextInt(100000, 999999)}",
                mode = mode,
                pingMs = Random.nextInt(18, 48),
                opponentName = opp1.first,
                opponentRating = opp1.second,
                opponentAvatar = Random.nextInt(1, 6),
                teammateName = if (mode == GameMode.TEAM_2V2) ally.first else null,
                teammateRating = if (mode == GameMode.TEAM_2V2) ally.second else null,
                teammateAvatar = if (mode == GameMode.TEAM_2V2) Random.nextInt(1, 6) else null,
                opponent2Name = if (mode == GameMode.TEAM_2V2) opp2.first else null,
                opponent2Rating = if (mode == GameMode.TEAM_2V2) opp2.second else null,
                opponent2Avatar = if (mode == GameMode.TEAM_2V2) Random.nextInt(1, 6) else null
            )

            _matchedGame.value = matchInfo
            _matchStatus.value = MatchmakingStatus.OPPONENT_FOUND

            delay(2000)
            _matchStatus.value = MatchmakingStatus.READY_CHECK
            delay(1500)
            _matchStatus.value = MatchmakingStatus.STARTING_MATCH
        }
    }

    fun cancelMatchmaking() {
        searchJob?.cancel()
        _matchStatus.value = MatchmakingStatus.IDLE
        _matchedGame.value = null
        _searchTimeSec.value = 0
    }

    // Custom Lobby Operations
    fun createLobby(
        hostPlayerId: String,
        hostPlayerName: String,
        hostAvatar: Int,
        hostRating: Int,
        mode: GameMode = GameMode.TEAM_2V2
    ): LobbyRoom {
        val roomCode = generateRoomCode()
        val totalSlots = if (mode == GameMode.DUEL_1V1) 2 else 4

        val slots = (0 until totalSlots).map { idx ->
            val team = if (idx < totalSlots / 2) Team.BLUE else Team.RED
            if (idx == 0) {
                LobbySlot(
                    slotIndex = 0,
                    team = Team.BLUE,
                    playerId = hostPlayerId,
                    playerName = hostPlayerName,
                    avatarIndex = hostAvatar,
                    rating = hostRating,
                    isHost = true,
                    isReady = true
                )
            } else {
                LobbySlot(
                    slotIndex = idx,
                    team = team,
                    playerId = null,
                    playerName = "Empty Slot (Tap to Invite/Add Bot)",
                    isHost = false,
                    isBot = false,
                    isReady = false
                )
            }
        }

        val lobby = LobbyRoom(
            roomCode = roomCode,
            hostPlayerId = hostPlayerId,
            hostPlayerName = hostPlayerName,
            mode = mode,
            slots = slots
        )

        _currentLobby.value = lobby
        _lobbyMessages.value = listOf(
            LobbyChatMessage(
                id = "msg_0",
                senderName = "System",
                text = "Lobby created! Share room code $roomCode with your friends.",
                isSystem = true
            )
        )
        return lobby
    }

    fun joinLobbyByCode(
        roomCode: String,
        playerId: String,
        playerName: String,
        avatarIndex: Int,
        rating: Int
    ): Boolean {
        val current = _currentLobby.value
        val targetCode = roomCode.trim().uppercase()

        if (current != null && current.roomCode == targetCode) {
            // Find first empty slot
            val emptyIndex = current.slots.indexOfFirst { !it.isOccupied }
            if (emptyIndex != -1) {
                val updatedSlots = current.slots.toMutableList()
                val targetSlot = updatedSlots[emptyIndex]
                updatedSlots[emptyIndex] = targetSlot.copy(
                    playerId = playerId,
                    playerName = playerName,
                    avatarIndex = avatarIndex,
                    rating = rating,
                    isHost = false,
                    isBot = false,
                    isReady = true
                )
                _currentLobby.value = current.copy(slots = updatedSlots)
                addLobbyMessage(playerName, "Joined the lobby!")
                return true
            }
        } else {
            // Create a mock joined lobby room for entered room code
            val slots = listOf(
                LobbySlot(
                    slotIndex = 0,
                    team = Team.BLUE,
                    playerId = "CC-HOST",
                    playerName = "LobbyHost",
                    avatarIndex = 1,
                    rating = 1350,
                    isHost = true,
                    isReady = true
                ),
                LobbySlot(
                    slotIndex = 1,
                    team = Team.BLUE,
                    playerId = playerId,
                    playerName = playerName,
                    avatarIndex = avatarIndex,
                    rating = rating,
                    isHost = false,
                    isReady = true
                ),
                LobbySlot(
                    slotIndex = 2,
                    team = Team.RED,
                    playerId = "bot_nemesis_1",
                    playerName = "Nemesis-X [Bot]",
                    avatarIndex = 2,
                    rating = 1300,
                    isHost = false,
                    isBot = true,
                    isReady = true
                ),
                LobbySlot(
                    slotIndex = 3,
                    team = Team.RED,
                    playerId = "bot_nemesis_2",
                    playerName = "Nemesis-Y [Bot]",
                    avatarIndex = 3,
                    rating = 1300,
                    isHost = false,
                    isBot = true,
                    isReady = true
                )
            )

            val joinedLobby = LobbyRoom(
                roomCode = targetCode,
                hostPlayerId = "CC-HOST",
                hostPlayerName = "LobbyHost",
                mode = GameMode.TEAM_2V2,
                slots = slots
            )
            _currentLobby.value = joinedLobby
            _lobbyMessages.value = listOf(
                LobbyChatMessage(
                    id = "msg_join",
                    senderName = "System",
                    text = "Connected to room $targetCode!",
                    isSystem = true
                )
            )
            return true
        }
        return false
    }

    fun toggleSlotBot(slotIndex: Int) {
        val lobby = _currentLobby.value ?: return
        if (slotIndex < 0 || slotIndex >= lobby.slots.size) return
        val currentSlot = lobby.slots[slotIndex]
        if (currentSlot.isHost) return // Can't change host

        val updatedSlots = lobby.slots.toMutableList()
        if (currentSlot.isBot) {
            // Clear bot to empty slot
            updatedSlots[slotIndex] = currentSlot.copy(
                playerId = null,
                playerName = "Empty Slot",
                isBot = false,
                isReady = false
            )
            addLobbyMessage("System", "Bot removed from Slot ${slotIndex + 1}", isSystem = true)
        } else if (currentSlot.playerId == null) {
            // Fill with bot
            val botName = if (currentSlot.team == Team.BLUE) "CyberAlly [Bot]" else "CyberNemesis [Bot]"
            updatedSlots[slotIndex] = currentSlot.copy(
                playerId = "bot_${slotIndex}",
                playerName = botName,
                avatarIndex = Random.nextInt(1, 6),
                rating = 1250,
                isBot = true,
                isReady = true
            )
            addLobbyMessage("System", "$botName added to Slot ${slotIndex + 1}", isSystem = true)
        }
        _currentLobby.value = lobby.copy(slots = updatedSlots)
    }

    fun inviteFriendToLobby(friendId: String, friendName: String, friendAvatar: Int) {
        val lobby = _currentLobby.value ?: return
        val emptyIndex = lobby.slots.indexOfFirst { !it.isOccupied }
        if (emptyIndex != -1) {
            // Simulate friend accepting invite after a brief delay
            scope.launch {
                addLobbyMessage("System", "Sent invite to $friendName...", isSystem = true)
                delay(2000)
                val currentL = _currentLobby.value ?: return@launch
                val currentEmpty = currentL.slots.indexOfFirst { !it.isOccupied }
                if (currentEmpty != -1) {
                    val updated = currentL.slots.toMutableList()
                    updated[currentEmpty] = updated[currentEmpty].copy(
                        playerId = friendId,
                        playerName = friendName,
                        avatarIndex = friendAvatar,
                        rating = 1320,
                        isBot = false,
                        isReady = true
                    )
                    _currentLobby.value = currentL.copy(slots = updated)
                    addLobbyMessage(friendName, "Joined lobby: Let's win this!")
                }
            }
        }
    }

    fun updateLobbySettings(mode: GameMode, map: ArenaTheme, durationSec: Int) {
        val lobby = _currentLobby.value ?: return
        val totalSlots = if (mode == GameMode.DUEL_1V1) 2 else 4
        val newSlots = if (totalSlots != lobby.slots.size) {
            (0 until totalSlots).map { idx ->
                if (idx < lobby.slots.size) {
                    val prev = lobby.slots[idx]
                    prev.copy(team = if (idx < totalSlots / 2) Team.BLUE else Team.RED)
                } else {
                    LobbySlot(
                        slotIndex = idx,
                        team = if (idx < totalSlots / 2) Team.BLUE else Team.RED
                    )
                }
            }
        } else {
            lobby.slots
        }

        _currentLobby.value = lobby.copy(
            mode = mode,
            arenaTheme = map,
            matchDurationSec = durationSec,
            slots = newSlots
        )
    }

    fun addLobbyMessage(sender: String, text: String, isSystem: Boolean = false) {
        val list = _lobbyMessages.value.toMutableList()
        list.add(
            LobbyChatMessage(
                id = "msg_${System.currentTimeMillis()}",
                senderName = sender,
                text = text,
                isSystem = isSystem
            )
        )
        _lobbyMessages.value = list
    }

    fun leaveLobby() {
        _currentLobby.value = null
        _lobbyMessages.value = emptyList()
    }

    fun sendSimulatedInvite(fromFriendName: String, fromFriendId: String, fromFriendAvatar: Int) {
        _incomingInvite.value = FriendInvite(
            inviteId = "INV-${Random.nextInt(1000, 9999)}",
            fromPlayerId = fromFriendId,
            fromPlayerName = fromFriendName,
            fromPlayerAvatar = fromFriendAvatar,
            roomCode = generateRoomCode(),
            mode = GameMode.TEAM_2V2
        )
    }

    fun dismissInvite() {
        _incomingInvite.value = null
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "CC-" + (1..4).map { chars.random() }.joinToString("")
    }
}
