package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FriendEntity
import com.example.game.engine.ArenaTheme
import com.example.game.engine.GameMode
import com.example.game.engine.Team
import com.example.game.multiplayer.LobbyChatMessage
import com.example.game.multiplayer.LobbyRoom
import com.example.game.multiplayer.LobbySlot
import com.example.ui.components.AvatarBadge
import com.example.ui.components.CyberButton
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonGold
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TeamBlue
import com.example.ui.theme.TeamRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LobbyScreen(
    lobby: LobbyRoom?,
    messages: List<LobbyChatMessage>,
    friends: List<FriendEntity>,
    onToggleSlotBot: (Int) -> Unit,
    onInviteFriend: (FriendEntity) -> Unit,
    onUpdateSettings: (GameMode, ArenaTheme, Int) -> Unit,
    onSendMessage: (String) -> Unit,
    onStartMatch: () -> Unit,
    onLeaveLobby: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var chatInput by remember { mutableStateOf("") }
    var showFriendInviteSheet by remember { mutableStateOf(false) }

    if (lobby == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active lobby.", color = TextMuted)
                Spacer(Modifier.height(12.dp))
                CyberButton(text = "Go Back", onClick = onLeaveLobby)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onLeaveLobby,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CyberSurface)
                        .testTag("lobby_leave_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Leave Lobby",
                        tint = NeonCyan
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "INVITE-ONLY LOBBY",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Host: ${lobby.hostPlayerName}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x3300FF88))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "LIVE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = NeonEmerald)
                }
            }
        }

        // Room Code Banner with 1-Tap Copy
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        clipboardManager.setText(AnnotatedString(lobby.roomCode))
                        Toast.makeText(context, "Room Code copied: ${lobby.roomCode}", Toast.LENGTH_SHORT).show()
                    }
                    .testTag("copy_room_code_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
                border = BorderStroke(1.5.dp, NeonGold)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ROOM CODE (TAP TO COPY & SHARE)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGold
                        )
                        Text(
                            text = lobby.roomCode,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 2.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = NeonGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Mode & Arena Map Switchers
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = BorderStroke(1.dp, Color(0x3300F0FF))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Mode Toggle (1v1 Duel vs 2v2 Team Clash)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (lobby.mode == GameMode.DUEL_1V1) NeonCyan else Color(0x22FFFFFF))
                                .clickable { onUpdateSettings(GameMode.DUEL_1V1, lobby.arenaTheme, lobby.matchDurationSec) }
                                .padding(vertical = 8.dp)
                                .testTag("lobby_mode_1v1_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "1v1 DUEL",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = if (lobby.mode == GameMode.DUEL_1V1) Color(0xFF0A0E1A) else TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (lobby.mode == GameMode.TEAM_2V2) NeonMagenta else Color(0x22FFFFFF))
                                .clickable { onUpdateSettings(GameMode.TEAM_2V2, lobby.arenaTheme, lobby.matchDurationSec) }
                                .padding(vertical = 8.dp)
                                .testTag("lobby_mode_2v2_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "2v2 TEAM CLASH",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = if (lobby.mode == GameMode.TEAM_2V2) Color.White else TextSecondary
                            )
                        }
                    }

                    // Map Name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "ARENA MAP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(
                            text = lobby.arenaTheme.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                }
            }
        }

        // Slot Management Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LOBBY SLOTS (${lobby.slots.count { it.isOccupied }}/${lobby.slots.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                TextButton(
                    onClick = { showFriendInviteSheet = !showFriendInviteSheet },
                    modifier = Modifier.testTag("lobby_invite_friend_button")
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                    Spacer(Modifier.width(4.dp))
                    Text(text = "INVITE FRIEND", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                }
            }
        }

        // Render Slots
        items(lobby.slots) { slot ->
            val slotTeamColor = if (slot.team == Team.BLUE) TeamBlue else TeamRed
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = BorderStroke(1.dp, if (slot.isOccupied) slotTeamColor else Color(0x22FFFFFF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (slot.isOccupied) {
                            AvatarBadge(avatarIndex = slot.avatarIndex, sizeDp = 40, borderColor = slotTeamColor)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = TextMuted)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = slot.playerName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (slot.isOccupied) TextPrimary else TextMuted
                                )
                                if (slot.isHost) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x33FFB703))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("HOST", fontSize = 8.sp, fontWeight = FontWeight.Black, color = NeonGold)
                                    }
                                }
                            }
                            Text(
                                text = "Slot ${slot.slotIndex + 1} • ${if (slot.team == Team.BLUE) "Team Blue" else "Team Red"}",
                                fontSize = 11.sp,
                                color = slotTeamColor
                            )
                        }
                    }

                    // Action for slot: Toggle Bot or Ready
                    if (!slot.isHost) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (slot.isBot) Color(0x33FF3366) else Color(0x3300F0FF))
                                .clickable { onToggleSlotBot(slot.slotIndex) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("slot_toggle_bot_${slot.slotIndex}")
                        ) {
                            Text(
                                text = if (slot.isBot) "REMOVE BOT" else if (slot.playerId == null) "+ ADD BOT" else "READY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (slot.isBot) NeonCrimson else NeonCyan
                            )
                        }
                    } else {
                        Text(
                            text = "READY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonEmerald
                        )
                    }
                }
            }
        }

        // Friends Direct Invite List (Collapsible)
        if (showFriendInviteSheet) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
                    border = BorderStroke(1.dp, NeonCyan)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "INVITE FROM FRIEND LIST",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonCyan
                        )

                        if (friends.isEmpty()) {
                            Text("No friends available.", fontSize = 12.sp, color = TextMuted)
                        } else {
                            friends.forEach { f ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AvatarBadge(avatarIndex = f.avatarIndex, sizeDp = 32, isOnline = f.isOnline, showOnlineStatus = true)
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = f.displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }

                                    CyberButton(
                                        text = "INVITE",
                                        onClick = {
                                            onInviteFriend(f)
                                            showFriendInviteSheet = false
                                            Toast.makeText(context, "Invited ${f.displayName} to lobby!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.height(36.dp),
                                        color = NeonCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // In-Lobby Quick Chat
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = BorderStroke(1.dp, Color(0x22FFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "LOBBY CHAT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )

                    // Quick tactical messages
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Ready!", "Let's win!", "Swap slots!", "Nice!").forEach { qm ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x2200F0FF))
                                    .clickable { onSendMessage(qm) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(text = qm, fontSize = 10.sp, color = NeonCyan)
                            }
                        }
                    }

                    // Message list
                    messages.takeLast(4).forEach { msg ->
                        Text(
                            text = "${msg.senderName}: ${msg.text}",
                            fontSize = 11.sp,
                            color = if (msg.isSystem) NeonGold else TextPrimary
                        )
                    }

                    // Send text row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Type message...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lobby_chat_input")
                        )

                        IconButton(
                            onClick = {
                                if (chatInput.isNotBlank()) {
                                    onSendMessage(chatInput)
                                    chatInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NeonCyan)
                                .testTag("lobby_send_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color(0xFF0A0E1A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Host Launch Game Button
        item {
            CyberButton(
                text = "START BATTLE",
                onClick = onStartMatch,
                icon = Icons.Default.PlayArrow,
                color = NeonEmerald,
                textColor = Color(0xFF002914),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lobby_start_battle_button")
            )
        }
    }
}
