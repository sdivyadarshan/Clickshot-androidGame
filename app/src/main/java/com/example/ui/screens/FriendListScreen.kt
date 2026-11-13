package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
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
import com.example.data.model.UserProfile
import com.example.ui.components.AvatarBadge
import com.example.ui.components.CyberButton
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonGold
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FriendListScreen(
    userProfile: UserProfile?,
    friends: List<FriendEntity>,
    onAddFriend: (String, String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onChallengeFriend: (FriendEntity) -> Unit,
    onSimulateIncomingInvite: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var friendIdInput by remember { mutableStateOf("") }
    var friendNameInput by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(horizontal = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CyberSurface)
                        .testTag("friends_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NeonCyan
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "FRIEND ROSTER",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Squad members & Online rivals",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            IconButton(
                onClick = { showAddDialog = !showAddDialog },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
                    .testTag("toggle_add_friend_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add Friend",
                    tint = Color(0xFF0A0E1A)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // My Player ID Share Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val id = userProfile?.playerId ?: "CC-7842"
                            clipboardManager.setText(AnnotatedString(id))
                            Toast.makeText(context, "Copied your Player ID: $id", Toast.LENGTH_SHORT).show()
                        }
                        .testTag("copy_my_player_id_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceElevated),
                    border = BorderStroke(1.dp, NeonCyan)
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
                                text = "MY PILOT ID (TAP TO COPY)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Text(
                                text = userProfile?.playerId ?: "CC-7842",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy ID",
                            tint = NeonCyan
                        )
                    }
                }
            }

            // Test Simulation Trigger (Helps verify invite functionality anytime)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1438)),
                    border = BorderStroke(1.dp, NeonMagenta)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, tint = NeonMagenta)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(text = "SIMULATE INCOMING INVITE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = NeonMagenta)
                                Text(text = "Receive a real-time lobby invitation from a friend", fontSize = 10.sp, color = TextSecondary)
                            }
                        }

                        CyberButton(
                            text = "RECEIVE",
                            onClick = onSimulateIncomingInvite,
                            color = NeonMagenta,
                            textColor = Color.White,
                            modifier = Modifier.height(36.dp).testTag("trigger_test_invite_button")
                        )
                    }
                }
            }

            // Add Friend Form
            if (showAddDialog) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = BorderStroke(1.5.dp, NeonCyan)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "ADD FRIEND BY PLAYER ID",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonCyan
                            )

                            OutlinedTextField(
                                value = friendIdInput,
                                onValueChange = { friendIdInput = it.uppercase() },
                                label = { Text("Friend's Player ID (e.g. CC-4421)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("add_friend_id_input")
                            )

                            OutlinedTextField(
                                value = friendNameInput,
                                onValueChange = { friendNameInput = it },
                                label = { Text("Friend's Nickname (Optional)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("add_friend_name_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showAddDialog = false }) {
                                    Text("CANCEL", color = TextMuted)
                                }
                                Spacer(Modifier.width(8.dp))
                                CyberButton(
                                    text = "ADD FRIEND",
                                    onClick = {
                                        if (friendIdInput.isNotBlank()) {
                                            onAddFriend(friendIdInput, friendNameInput)
                                            friendIdInput = ""
                                            friendNameInput = ""
                                            showAddDialog = false
                                            Toast.makeText(context, "Friend Added!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = friendIdInput.isNotBlank(),
                                    modifier = Modifier.testTag("submit_add_friend_button")
                                )
                            }
                        }
                    }
                }
            }

            // Friends List Header
            item {
                Text(
                    text = "ALL FRIENDS (${friends.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            }

            if (friends.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberSurface)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No friends added. Tap '+' above to add friends!", color = TextMuted)
                    }
                }
            } else {
                items(friends) { friend ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = BorderStroke(1.dp, if (friend.isOnline) Color(0x3300FF88) else Color(0x11FFFFFF))
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
                                AvatarBadge(
                                    avatarIndex = friend.avatarIndex,
                                    sizeDp = 44,
                                    isOnline = friend.isOnline,
                                    showOnlineStatus = true
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = friend.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TextPrimary
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "[${friend.friendId}]",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Text(
                                        text = "${friend.statusMessage} • ${friend.rating} Elo",
                                        fontSize = 11.sp,
                                        color = if (friend.isOnline) NeonEmerald else TextMuted
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CyberButton(
                                    text = "1v1",
                                    onClick = { onChallengeFriend(friend) },
                                    color = NeonCyan,
                                    modifier = Modifier.height(36.dp).testTag("challenge_friend_${friend.friendId}")
                                )

                                IconButton(
                                    onClick = { onRemoveFriend(friend.friendId) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Friend",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
