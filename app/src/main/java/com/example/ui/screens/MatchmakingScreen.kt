package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.game.engine.GameMode
import com.example.game.multiplayer.MatchFoundInfo
import com.example.game.multiplayer.MatchmakingStatus
import com.example.ui.components.AvatarBadge
import com.example.ui.components.CyberButton
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
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
fun MatchmakingScreen(
    status: MatchmakingStatus,
    searchTimeSec: Int,
    matchedInfo: MatchFoundInfo?,
    userProfile: UserProfile?,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRotate"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (status == MatchmakingStatus.SEARCHING) "MATCHMAKING QUEUE" else "OPPONENT LOCATED!",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = if (status == MatchmakingStatus.SEARCHING) NeonCyan else NeonEmerald,
                letterSpacing = 1.sp
            )
            Text(
                text = if (status == MatchmakingStatus.SEARCHING) "Searching real-time peer network..." else "Locking arena coordinates...",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Center Content: Radar Scanner or Matched Matchup Screen
        if (status == MatchmakingStatus.SEARCHING || matchedInfo == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Radar Box
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(CyberSurface)
                        .border(2.dp, Brush.radialGradient(listOf(NeonCyan, Color.Transparent)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().rotate(rotationAngle)) {
                        val center = this.center
                        drawCircle(
                            color = NeonCyan.copy(alpha = 0.15f),
                            radius = size.minDimension * 0.4f,
                            style = Stroke(width = 2f)
                        )
                        drawCircle(
                            color = NeonCyan.copy(alpha = 0.25f),
                            radius = size.minDimension * 0.25f,
                            style = Stroke(width = 2f)
                        )
                        drawLine(
                            brush = Brush.radialGradient(listOf(NeonCyan, Color.Transparent)),
                            start = center,
                            end = center.copy(x = size.width, y = center.y),
                            strokeWidth = 3f
                        )
                    }

                    AvatarBadge(avatarIndex = userProfile?.avatarIndex ?: 0, sizeDp = 64)
                }

                // Time Elapsed
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Searching: ${searchTimeSec}s",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonGold
                    )
                    Text(
                        text = "Estimated wait: ~4s • Rating +/- 150 Elo",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            // Match Found Matchup Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode & Ping Pill
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x3300F0FF))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = matchedInfo.mode.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonCyan
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x3300FF88))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "📶 ${matchedInfo.pingMs} ms",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonEmerald
                        )
                    }
                }

                // Team Blue (You + Teammate)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(1.5.dp, TeamBlue)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarBadge(avatarIndex = userProfile?.avatarIndex ?: 0, sizeDp = 44, borderColor = TeamBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${userProfile?.displayName ?: "CyberPilot"} (YOU)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TeamBlue
                            )
                            Text(
                                text = "Rating: ${userProfile?.rating ?: 1200} Elo",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        if (matchedInfo.mode == GameMode.TEAM_2V2 && matchedInfo.teammateName != null) {
                            AvatarBadge(avatarIndex = matchedInfo.teammateAvatar ?: 1, sizeDp = 36, borderColor = TeamBlue)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = matchedInfo.teammateName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${matchedInfo.teammateRating ?: 1250} Elo",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // VS Glow Badge
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(NeonGold)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "VS",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color(0xFF0A0E1A)
                    )
                }

                // Team Red (Opponent 1 + Opponent 2)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(1.5.dp, TeamRed)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarBadge(avatarIndex = matchedInfo.opponentAvatar, sizeDp = 44, borderColor = TeamRed)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = matchedInfo.opponentName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TeamRed
                            )
                            Text(
                                text = "Rating: ${matchedInfo.opponentRating} Elo",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        if (matchedInfo.mode == GameMode.TEAM_2V2 && matchedInfo.opponent2Name != null) {
                            AvatarBadge(avatarIndex = matchedInfo.opponent2Avatar ?: 2, sizeDp = 36, borderColor = TeamRed)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = matchedInfo.opponent2Name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${matchedInfo.opponent2Rating ?: 1280} Elo",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Button
        if (status == MatchmakingStatus.SEARCHING) {
            CyberButton(
                text = "Cancel Search",
                onClick = onCancel,
                color = Color(0x33FF3366),
                textColor = Color(0xFFFF3366),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cancel_matchmaking_button")
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = NeonCyan,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "DEPLOYING TO ARENA...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan
                )
            }
        }
    }
}
