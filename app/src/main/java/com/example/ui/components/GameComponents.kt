package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.multiplayer.FriendInvite
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonGold
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.TeamBlue
import com.example.ui.theme.TeamRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Virtual Movement Joystick
@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    onMove: (x: Float, y: Float) -> Unit
) {
    val maxRadius = 55f
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .size(130.dp)
            .clip(CircleShape)
            .background(Color(0x55131B2E))
            .border(2.dp, Brush.radialGradient(listOf(NeonCyan, Color.Transparent)), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        offsetX = 0f
                        offsetY = 0f
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        offsetX = 0f
                        offsetY = 0f
                        onMove(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = offsetX + dragAmount.x
                        val newY = offsetY + dragAmount.y
                        val dist = sqrt(newX * newX + newY * newY)

                        if (dist <= maxRadius) {
                            offsetX = newX
                            offsetY = newY
                        } else {
                            val angle = atan2(newY.toDouble(), newX.toDouble())
                            offsetX = (cos(angle) * maxRadius).toFloat()
                            offsetY = (sin(angle) * maxRadius).toFloat()
                        }
                        onMove(offsetX / maxRadius, offsetY / maxRadius)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Inner glowing thumb knob
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(50.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF00F0FF), Color(0xFF007788))
                    )
                )
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

// Action Buttons Bar (Fire, Shield, Dash, Ultimate)
@Composable
fun ActionControlsBar(
    modifier: Modifier = Modifier,
    energy: Float,
    shieldActive: Boolean,
    dashCooldown: Float,
    isFiring: Boolean,
    onFireToggle: (Boolean) -> Unit,
    onShieldClick: () -> Unit,
    onDashClick: () -> Unit,
    onUltimateClick: () -> Unit
) {
    val isUltReady = energy >= 90f
    val infiniteTransition = rememberInfiniteTransition(label = "ult")
    val ultGlow by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ultGlow"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top row: Dash & Shield & Ult
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dash Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (energy >= 25f && dashCooldown <= 0f) CyberSurfaceElevated else Color(0x331C2744))
                    .border(
                        1.5.dp,
                        if (energy >= 25f && dashCooldown <= 0f) NeonGold else Color(0x33FFFFFF),
                        CircleShape
                    )
                    .clickable(enabled = energy >= 25f && dashCooldown <= 0f) { onDashClick() }
                    .testTag("action_dash_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Phase Dash",
                        tint = if (energy >= 25f && dashCooldown <= 0f) NeonGold else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (dashCooldown > 0f) "${dashCooldown.toInt()}s" else "DASH",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (energy >= 25f && dashCooldown <= 0f) NeonGold else TextMuted
                    )
                }
            }

            // Shield Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (shieldActive) Color(0x6600F0FF) else if (energy >= 30f) CyberSurfaceElevated else Color(0x331C2744))
                    .border(
                        1.5.dp,
                        if (shieldActive) Color.White else if (energy >= 30f) NeonCyan else Color(0x33FFFFFF),
                        CircleShape
                    )
                    .clickable(enabled = energy >= 30f && !shieldActive) { onShieldClick() }
                    .testTag("action_shield_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Kinetic Shield",
                        tint = if (shieldActive || energy >= 30f) NeonCyan else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (shieldActive) "ACTIVE" else "SHIELD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (shieldActive || energy >= 30f) NeonCyan else TextMuted
                    )
                }
            }

            // Ultimate Button
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .scale(if (isUltReady) ultGlow else 1f)
                    .clip(CircleShape)
                    .background(
                        if (isUltReady) {
                            Brush.radialGradient(listOf(NeonMagenta, Color(0xFF660033)))
                        } else {
                            Brush.radialGradient(listOf(Color(0x33FF007F), Color(0x11111111)))
                        }
                    )
                    .border(
                        2.dp,
                        if (isUltReady) NeonMagenta else Color(0x33FF007F),
                        CircleShape
                    )
                    .clickable(enabled = isUltReady) { onUltimateClick() }
                    .testTag("action_ultimate_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Overdrive Ultimate",
                        tint = if (isUltReady) Color.White else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "NOVA",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isUltReady) Color.White else TextMuted
                    )
                }
            }
        }

        // Primary Blaster Fire Trigger
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (isFiring) {
                        Brush.radialGradient(listOf(Color(0xFFFF3366), Color(0xFF990033)))
                    } else {
                        Brush.radialGradient(listOf(NeonCyan, Color(0xFF005577)))
                    }
                )
                .border(2.5.dp, Color.White, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onFireToggle(true)
                            tryAwaitRelease()
                            onFireToggle(false)
                        }
                    )
                }
                .testTag("action_fire_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "FIRE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "BLASTER",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xCCFFFFFF)
                )
            }
        }
    }
}

// Top Score & Status HUD
@Composable
fun ArenaHUD(
    blueScore: Int,
    redScore: Int,
    targetScore: Int,
    timeRemainingSec: Float,
    health: Float,
    maxHealth: Float,
    energy: Float,
    maxEnergy: Float,
    modifierName: String,
    onPauseClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD0A0E1A)),
        border = BorderStroke(1.dp, Color(0x4400F0FF)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Blue Team Score
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(TeamBlue)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "BLUE: $blueScore",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = TeamBlue
                    )
                }

                // Match Time & Target
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${timeRemainingSec.toInt()}s",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (timeRemainingSec <= 15f) NeonCrimson else NeonGold
                    )
                    Text(
                        text = "FIRST TO $targetScore",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted
                    )
                }

                // Red Team Score
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$redScore :RED",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = TeamRed
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(TeamRed)
                    )
                }
            }

            // Health & Energy Progress Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Health Bar
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "HULL HP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                        Text(
                            text = "${health.toInt()}/${maxHealth.toInt()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { (health / maxHealth).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (health > 30f) NeonEmerald else NeonCrimson,
                        trackColor = Color(0x3300FF88)
                    )
                }

                // Energy Bar
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ENERGY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Text(
                            text = "${energy.toInt()}/${maxEnergy.toInt()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { (energy / maxEnergy).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonCyan,
                        trackColor = Color(0x3300F0FF)
                    )
                }
            }
        }
    }
}

// Tactical Emotes Quick Bar
@Composable
fun TacticalEmoteBar(
    modifier: Modifier = Modifier,
    onEmoteSelected: (String) -> Unit
) {
    val emotes = listOf("🔥", "⚡", "🛡️", "💀", "👍")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xAA0A0E1A))
            .border(1.dp, Color(0x3300F0FF), RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        emotes.forEach { emote ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onEmoteSelected(emote) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emote, fontSize = 16.sp)
            }
        }
    }
}

// Sleek Styled Cyber Button
@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = NeonCyan,
    textColor: Color = Color(0xFF001B24),
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(52.dp)
            .shadow(if (enabled) 8.dp else 0.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = textColor,
            disabledContainerColor = Color(0x33334155),
            disabledContentColor = TextMuted
        ),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text.uppercase(),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 0.75.sp
            )
        }
    }
}

// Avatar Badge
@Composable
fun AvatarBadge(
    avatarIndex: Int,
    sizeDp: Int = 48,
    isOnline: Boolean = true,
    showOnlineStatus: Boolean = false,
    borderColor: Color = NeonCyan
) {
    val avatarIcons = listOf("⚡", "🚀", "👾", "🤖", "⚔️", "🌟")
    val symbol = avatarIcons.getOrElse(avatarIndex % avatarIcons.size) { "⚡" }

    Box(
        modifier = Modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(CyberSurfaceElevated)
                .border(2.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = symbol, fontSize = (sizeDp * 0.45f).sp)
        }

        if (showOnlineStatus) {
            Box(
                modifier = Modifier
                    .size((sizeDp * 0.32f).dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (isOnline) NeonEmerald else TextMuted)
                    .border(1.5.dp, CyberBackground, CircleShape)
            )
        }
    }
}

// Incoming Friend Lobby Invite Toast Banner
@Composable
fun IncomingInviteBanner(
    invite: FriendInvite?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AnimatedVisibility(
        visible = invite != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (invite != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .testTag("incoming_invite_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1438)),
                border = BorderStroke(1.5.dp, NeonMagenta)
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
                        AvatarBadge(avatarIndex = invite.fromPlayerAvatar, sizeDp = 40, borderColor = NeonMagenta)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "LOBBY INVITATION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonMagenta
                            )
                            Text(
                                text = "${invite.fromPlayerName} invited you!",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Room: ${invite.roomCode} • ${invite.mode.title}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onDecline,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FF3366))
                                .testTag("decline_invite_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Decline",
                                tint = NeonCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onAccept,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeonEmerald)
                                .testTag("accept_invite_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Accept",
                                tint = Color(0xFF002914),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
