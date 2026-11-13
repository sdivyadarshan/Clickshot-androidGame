package com.example.game.multiplayer

import com.example.game.engine.ArenaTheme
import com.example.game.engine.GameMode
import com.example.game.engine.Team

data class LobbySlot(
    val slotIndex: Int,
    val team: Team,
    val playerId: String? = null,
    val playerName: String = "Empty Slot",
    val avatarIndex: Int = 0,
    val rating: Int = 1200,
    val isHost: Boolean = false,
    val isBot: Boolean = false,
    val isReady: Boolean = false
) {
    val isOccupied: Boolean get() = playerId != null || isBot
}

data class LobbyRoom(
    val roomCode: String,
    val hostPlayerId: String,
    val hostPlayerName: String,
    val mode: GameMode = GameMode.TEAM_2V2,
    val arenaTheme: ArenaTheme = ArenaTheme.MINIMAL_SLATE,
    val matchDurationSec: Int = 90,
    val targetScore: Int = 10,
    val isInviteOnly: Boolean = true,
    val slots: List<LobbySlot>,
    val isGameStarting: Boolean = false
)

enum class MatchmakingStatus {
    IDLE,
    SEARCHING,
    OPPONENT_FOUND,
    READY_CHECK,
    STARTING_MATCH,
    ERROR
}

data class MatchFoundInfo(
    val matchId: String,
    val mode: GameMode,
    val pingMs: Int,
    val opponentName: String,
    val opponentRating: Int,
    val opponentAvatar: Int,
    val teammateName: String? = null,
    val teammateRating: Int? = null,
    val teammateAvatar: Int? = null,
    val opponent2Name: String? = null,
    val opponent2Rating: Int? = null,
    val opponent2Avatar: Int? = null
)

data class FriendInvite(
    val inviteId: String,
    val fromPlayerId: String,
    val fromPlayerName: String,
    val fromPlayerAvatar: Int,
    val roomCode: String,
    val mode: GameMode,
    val timestamp: Long = System.currentTimeMillis()
)

data class LobbyChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val isSystem: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
