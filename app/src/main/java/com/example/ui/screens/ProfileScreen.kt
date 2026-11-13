package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MatchHistoryEntity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    recentMatches: List<MatchHistoryEntity>,
    onUpdateProfile: (String, Int) -> Unit,
    onBack: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var nameInput by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "CyberPilot") }
    var selectedAvatar by remember(profile?.avatarIndex) { mutableIntStateOf(profile?.avatarIndex ?: 0) }

    val avatarSymbols = listOf("⚡", "🚀", "👾", "🤖", "⚔️", "🌟")

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CyberSurface)
                    .testTag("profile_back_button")
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
                    text = "PILOT PROFILE & CAREER",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Combat history, ratings and rank badge",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(1.dp, NeonCyan)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AvatarBadge(avatarIndex = selectedAvatar, sizeDp = 56, borderColor = NeonCyan)
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    if (isEditing) {
                                        OutlinedTextField(
                                            value = nameInput,
                                            onValueChange = { nameInput = it },
                                            singleLine = true,
                                            modifier = Modifier.width(160.dp).testTag("edit_name_input")
                                        )
                                    } else {
                                        Text(
                                            text = profile?.displayName ?: "CyberPilot",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "ID: ${profile?.playerId ?: "CC-7842"}",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (isEditing) {
                                        onUpdateProfile(nameInput, selectedAvatar)
                                    }
                                    isEditing = !isEditing
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(CyberSurfaceElevated)
                                    .testTag("toggle_edit_profile_button")
                            ) {
                                Icon(
                                    imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = if (isEditing) NeonEmerald else NeonCyan
                                )
                            }
                        }

                        // Avatar Picker (if editing)
                        if (isEditing) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "CHOOSE AVATAR ICON", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(avatarSymbols.size) { idx ->
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(if (selectedAvatar == idx) NeonCyan else CyberSurfaceElevated)
                                                .clickable { selectedAvatar = idx },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = avatarSymbols[idx], fontSize = 18.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Rank Tier & Elo Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberSurfaceElevated)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MilitaryTech,
                                        contentDescription = null,
                                        tint = NeonGold,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = profile?.rankTier ?: "Rookie",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = NeonGold
                                        )
                                        Text(
                                            text = "Rank Tier",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Text(
                                    text = "${profile?.rating ?: 1200} ELO",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = NeonCyan
                                )
                            }
                        }
                    }
                }
            }

            // Stats Grid
            item {
                Text(
                    text = "COMBAT CAREER STATS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Win Rate
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", profile?.winRate ?: 0f)}%",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonEmerald
                            )
                            Text("Win Rate", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    // Total Wins
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${profile?.totalWins ?: 0}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonGold
                            )
                            Text("Total Wins", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    // Campaign Highest
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Sec ${profile?.highestCampaignLevel ?: 1}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonCyan
                            )
                            Text("Max Sector", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }

            // Match History Header
            item {
                Text(
                    text = "RECENT MATCH HISTORY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            }

            if (recentMatches.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberSurface)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No match history recorded yet. Play a match!", color = TextMuted)
                    }
                }
            } else {
                items(recentMatches) { match ->
                    val isVictory = match.result == "VICTORY"
                    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    val dateStr = sdf.format(Date(match.timestamp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = BorderStroke(1.dp, if (isVictory) Color(0x3300FF88) else Color(0x33FF3366))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = match.result,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = if (isVictory) NeonEmerald else Color(0xFFFF3366)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = match.mode.replace("_", " "),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = "$dateStr • ${match.difficultyModifier}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${match.scorePlayer} - ${match.scoreOpponent}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${if (match.ratingChange >= 0) "+" else ""}${match.ratingChange} Elo",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (match.ratingChange >= 0) NeonCyan else Color(0xFFFF3366)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
