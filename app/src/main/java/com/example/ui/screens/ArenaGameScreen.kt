package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CampaignStage
import com.example.game.engine.DebrisFragment
import com.example.game.engine.GameEngine
import com.example.game.engine.GameStatus
import com.example.game.engine.GunEntity
import com.example.game.engine.GunType
import com.example.game.engine.Particle
import com.example.game.engine.PlatformObstacle
import com.example.game.engine.RicochetBullet
import com.example.game.engine.TargetEntity
import kotlin.math.sin

@Composable
fun ArenaGameScreen(
    engine: GameEngine?,
    gameTick: Long,
    selectedGunType: GunType = GunType.PULSE_PISTOL,
    isPaused: Boolean = false,
    isLevelsMenuOpen: Boolean = false,
    isSoundEnabled: Boolean = true,
    campaignStages: List<CampaignStage> = emptyList(),
    onInitArena: (Float, Float) -> Unit = { _, _ -> },
    onInitArenaBounds: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onTapScreen: () -> Unit,
    onSelectGun: (GunType) -> Unit = {},
    onPauseGame: () -> Unit = {},
    onResumeGame: () -> Unit = {},
    onOpenLevelsMenu: () -> Unit = {},
    onCloseLevelsMenu: () -> Unit = {},
    onSelectLevel: (Int) -> Unit = {},
    onToggleSound: () -> Unit = {},
    onRestartLevel: () -> Unit,
    onNextLevel: () -> Unit,
    onExitArena: () -> Unit
) {
    if (engine == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E222A)),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading Clickshot...", color = Color.White, fontSize = 16.sp)
        }
        return
    }

    BackHandler(enabled = isLevelsMenuOpen || isPaused || engine.gameStatus == GameStatus.PLAYING) {
        when {
            isLevelsMenuOpen -> onCloseLevelsMenu()
            isPaused -> onResumeGame()
            engine.gameStatus == GameStatus.PLAYING -> onPauseGame()
        }
    }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E222A)) // Sleek dark outer background
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        // Calculate focused, centered arena playfield leaving room for header & bottom gun arsenal bar
        val paddingH = with(density) { 16.dp.toPx() }
        val headerH = with(density) { 68.dp.toPx() }
        val bottomBarH = with(density) { 92.dp.toPx() }

        val availableW = (screenW - paddingH * 2).coerceAtLeast(200f)
        val availableH = (screenH - headerH - bottomBarH).coerceAtLeast(300f)

        // Focused close-up arena aspect ratio (~1:1.45)
        val arenaW = availableW.coerceAtMost(with(density) { 480.dp.toPx() })
        val arenaH = availableH.coerceAtMost(arenaW * 1.48f)

        val arenaLeft = (screenW - arenaW) / 2f
        val arenaTop = headerH + (availableH - arenaH) / 2f
        val arenaRight = arenaLeft + arenaW
        val arenaBottom = arenaTop + arenaH

        LaunchedEffect(arenaLeft, arenaTop, arenaRight, arenaBottom) {
            if (arenaW > 100f && arenaH > 100f) {
                onInitArenaBounds(arenaLeft, arenaTop, arenaRight, arenaBottom)
                onInitArena(screenW, screenH)
            }
        }

        // Tap-to-Shoot Input & Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_canvas")
                .pointerInput(engine, isPaused, isLevelsMenuOpen) {
                    detectTapGestures(
                        onTap = {
                            if (!isPaused && !isLevelsMenuOpen && engine.gameStatus == GameStatus.PLAYING) {
                                onTapScreen()
                            }
                        }
                    )
                }
        ) {
            // Read gameTick to ensure smooth frame-by-frame recomposition
            val tick = gameTick

            // 1. Draw Close-up Bezel Framed Arena
            drawArenaFramedChamber(
                engine.arenaLeft,
                engine.arenaTop,
                engine.arenaRight,
                engine.arenaBottom
            )

            // 2. Draw Platform Obstacles
            engine.platformObstacle?.let { plat ->
                drawPlatform(plat)
            }
            engine.secondPlatformObstacle?.let { plat ->
                drawPlatform(plat)
            }

            // 3. Draw Target Grenade (if active)
            if (!engine.target.isDestroyed) {
                drawTarget(engine.target)
            }

            // 4. Draw Ricochet Bullets & Glow Trails
            engine.bullets.forEach { bullet ->
                drawRicochetBullet(bullet)
            }

            // 5. Draw Target Shatter Debris Fragments
            engine.debrisList.forEach { debris ->
                drawDebris(debris)
            }

            // 6. Draw Sparks & Particle Emitters
            engine.particles.forEach { particle ->
                drawParticle(particle)
            }

            // 7. Draw Player Gun (Close-up detail)
            if (!engine.playerGun.isDestroyed) {
                drawPlayerGun(engine.playerGun)
            }
        }

        // Top Header Bar (Levels Pill, Restart, Sound Toggle, Pause)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF151922),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenLevelsMenu() }
                    .testTag("levels_menu_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Open Levels Menu",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Level ${engine.levelNumber}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.testTag("level_indicator")
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { onRestartLevel() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reset_level_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart Level",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = { onToggleSound() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sound_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isSoundEnabled) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = if (isSoundEnabled) "Mute Sound" else "Enable Sound",
                        tint = if (isSoundEnabled) Color(0xFF38BDF8) else Color(0xFF64748B),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = { if (isPaused) onResumeGame() else onPauseGame() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pause_button")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume Game" else "Pause Game",
                        tint = Color(0xFFF1F5F9),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Idle Tap Hint
        if (engine.shotsFiredCount == 0 && engine.gameStatus == GameStatus.PLAYING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 104.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "TAP ARENA TO SHOOT • ${selectedGunType.tagline.uppercase()}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp,
                    color = Color(0xB3FFFFFF)
                )
            }
        }

        // Bottom Weapon Arsenal Selector Strip (Outside Arena Canvas)
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("gun_selector_row"),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(GunType.entries) { gunType ->
                val isSelected = gunType == selectedGunType
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) gunType.primaryColor.copy(alpha = 0.22f) else Color(0xFF151922),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) gunType.primaryColor else Color(0xFF334155)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectGun(gunType) }
                        .testTag("gun_chip_${gunType.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(gunType.primaryColor)
                        )
                        Column {
                            Text(
                                text = gunType.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                            Text(
                                text = if (gunType.pelletsPerShot > 1) {
                                    "${gunType.pelletsPerShot}x • ${gunType.maxBounces} Bnc"
                                } else {
                                    "${gunType.maxBounces} Bnc • ${(gunType.bulletSpeed / 100f).toInt()}x Spd"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) gunType.accentColor else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        // Level Complete Banner Overlay
        AnimatedVisibility(
            visible = engine.gameStatus == GameStatus.WON,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xF00F172A)
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .padding(24.dp)
                    .width(320.dp)
                    .testTag("win_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Level Complete",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.testTag("win_title")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Target Eliminated with ${engine.shotsFiredCount} ${if (engine.shotsFiredCount == 1) "shot" else "shots"}!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRestartLevel() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("retry_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RETRY", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onNextLevel() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("next_level_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00A2FF)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("NEXT", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { onOpenLevelsMenu() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("win_levels_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LEVELS MENU", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Out of Bounds Retry Overlay
        AnimatedVisibility(
            visible = engine.gameStatus == GameStatus.LOST,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xF01E1B2E)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .padding(24.dp)
                    .width(300.dp)
                    .testTag("lose_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TRY AGAIN",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFEF4444)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "The weapon went out of bounds.",
                        fontSize = 14.sp,
                        color = Color(0xFFCBD5E1)
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = { onRestartLevel() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("restart_after_loss_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("RESTART LEVEL", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { onOpenLevelsMenu() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lose_levels_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LEVELS MENU", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Pause Mode Overlay with Full Weapon Selection
        AnimatedVisibility(
            visible = isPaused && !isLevelsMenuOpen && engine.gameStatus == GameStatus.PLAYING,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB8090D16))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Block arena taps while paused */ },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFA121722)),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .testTag("pause_dialog")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PAUSED",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFF8FAFC)
                                )
                                Text(
                                    text = "Level ${engine.levelNumber} • ${engine.shotsFiredCount} shots",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onToggleSound() }
                                    .testTag("pause_sound_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSoundEnabled) {
                                            Icons.AutoMirrored.Filled.VolumeUp
                                        } else {
                                            Icons.AutoMirrored.Filled.VolumeOff
                                        },
                                        contentDescription = if (isSoundEnabled) "Sound On" else "Sound Off",
                                        tint = if (isSoundEnabled) Color(0xFF38BDF8) else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isSoundEnabled) "SFX ON" else "MUTED",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSoundEnabled) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "SELECT WEAPON",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            color = Color(0xFF94A3B8)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .testTag("pause_gun_list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(GunType.entries) { gunType ->
                                val isSelected = gunType == selectedGunType
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) {
                                        gunType.primaryColor.copy(alpha = 0.20f)
                                    } else {
                                        Color(0xFF1A202E)
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) gunType.primaryColor else Color(0xFF2A3447)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSelectGun(gunType) }
                                        .testTag("pause_gun_option_${gunType.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(gunType.primaryColor)
                                            )
                                            Column {
                                                Text(
                                                    text = gunType.displayName,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "${gunType.tagline} • ${gunType.maxBounces} Bounces • Mass ${gunType.mass}",
                                                    fontSize = 11.sp,
                                                    color = if (isSelected) gunType.accentColor else Color(0xFF94A3B8)
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Equipped",
                                                tint = gunType.accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { onResumeGame() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("resume_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A2FF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RESUME", color = Color.White, fontWeight = FontWeight.Black)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onOpenLevelsMenu() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pause_levels_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("LEVELS", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onRestartLevel() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pause_restart_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF94A3B8))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFFE2E8F0),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RESTART", color = Color(0xFFE2E8F0), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Levels Menu Grid Overlay
        AnimatedVisibility(
            visible = isLevelsMenuOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier.fillMaxSize()
        ) {
            val totalLevels = maxOf(20, engine.levelNumber + 2)
            val stageStarsMap = remember(campaignStages) {
                campaignStages.associate { it.levelNumber to it.stars }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xC8090D16))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Block arena taps while levels menu is open */ },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFA121722)),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .widthIn(max = 440.dp)
                        .testTag("levels_menu_dialog")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "LEVELS",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFF8FAFC)
                                )
                                Text(
                                    text = "1–5 Range Trials • 6+ Procedural Arenas",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            IconButton(
                                onClick = { onCloseLevelsMenu() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("close_levels_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Levels Menu",
                                    tint = Color(0xFFCBD5E1)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .testTag("levels_grid"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items((1..totalLevels).toList()) { lvl ->
                                val isCurrent = lvl == engine.levelNumber
                                val stars = stageStarsMap[lvl] ?: 0
                                val tag = when (lvl) {
                                    1 -> "Close"
                                    2 -> "Short"
                                    3 -> "Mid"
                                    4 -> "Long"
                                    5 -> "Max"
                                    else -> "Proc"
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = when {
                                        isCurrent -> Color(0xFF00A2FF).copy(alpha = 0.24f)
                                        stars > 0 -> Color(0xFF10B981).copy(alpha = 0.14f)
                                        else -> Color(0xFF1A202E)
                                    },
                                    border = BorderStroke(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = when {
                                            isCurrent -> Color(0xFF38BDF8)
                                            stars > 0 -> Color(0xFF10B981)
                                            else -> Color(0xFF2E384B)
                                        }
                                    ),
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSelectLevel(lvl) }
                                        .testTag("level_tile_$lvl")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$lvl",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isCurrent) Color(0xFF38BDF8) else Color.White
                                        )
                                        Text(
                                            text = tag,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                            (1..3).forEach { s ->
                                                Icon(
                                                    imageVector = if (s <= stars) {
                                                        Icons.Default.Star
                                                    } else {
                                                        Icons.Default.StarBorder
                                                    },
                                                    contentDescription = null,
                                                    tint = if (s <= stars) Color(0xFFFFD600) else Color(0xFF475569),
                                                    modifier = Modifier.size(10.dp)
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
        }
    }
}

// --- Canvas Drawing Routines ---

private fun DrawScope.drawArenaFramedChamber(left: Float, top: Float, right: Float, bottom: Float) {
    val arenaW = right - left
    val arenaH = bottom - top

    // Outer framing shadow/bezel
    drawRoundRect(
        color = Color(0xFF161A22),
        topLeft = Offset(left - 6f, top - 6f),
        size = Size(arenaW + 12f, arenaH + 12f),
        cornerRadius = CornerRadius(14f, 14f)
    )

    // Inner playfield dark vertical gradient
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF262D38), Color(0xFF1C222B)),
            startY = top,
            endY = bottom
        ),
        topLeft = Offset(left, top),
        size = Size(arenaW, arenaH),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Crisp arena boundary border stroke
    drawRoundRect(
        color = Color(0xFF3B485A),
        topLeft = Offset(left, top),
        size = Size(arenaW, arenaH),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 3.5.dp.toPx())
    )
}

private fun DrawScope.drawPlatform(plat: PlatformObstacle) {
    // Shelf platform body
    drawRoundRect(
        color = plat.color,
        topLeft = Offset(plat.x, plat.y),
        size = Size(plat.width, plat.height),
        cornerRadius = CornerRadius(5f, 5f)
    )

    // Top surface metallic edge highlight
    drawLine(
        color = Color(0xFF64748B),
        start = Offset(plat.x + 3f, plat.y + 2f),
        end = Offset(plat.x + plat.width - 3f, plat.y + 2f),
        strokeWidth = 2.5.dp.toPx()
    )
}

private fun DrawScope.drawPlayerGun(gun: GunEntity) {
    val angleDeg = Math.toDegrees(gun.angle.toDouble()).toFloat()
    val blowback = (gun.recoilKickTimer / 0.14f).coerceIn(0f, 1f) * (gun.width * 0.055f)

    rotate(degrees = angleDeg, pivot = Offset(gun.x, gun.y)) {
        val w = gun.width
        val h = gun.height
        val left = gun.x - w * 0.5f
        val top = gun.y - h * 0.5f

        when (gun.gunType) {
            GunType.PULSE_PISTOL -> drawPistolSilhouette(gun, left, top, w, h, blowback)
            GunType.HEAVY_DEAGLE -> drawMagnumSilhouette(gun, left, top, w, h, blowback)
            GunType.SCATTER_SHOTGUN -> drawShotgunSilhouette(gun, left, top, w, h, blowback)
            GunType.VECTOR_SMG -> drawSmgSilhouette(gun, left, top, w, h, blowback)
            GunType.RAIL_CANNON -> drawRailgunSilhouette(gun, left, top, w, h, blowback)
        }
    }
}

private fun DrawScope.drawPistolSilhouette(
    gun: GunEntity,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    blowback: Float
) {
    // 1. Pistol Grip / Handle
    val gripPath = Path().apply {
        moveTo(left + w * 0.18f, top + h * 0.46f)
        lineTo(left + w * 0.44f, top + h * 0.46f)
        lineTo(left + w * 0.34f, top + h * 0.98f)
        lineTo(left + w * 0.08f, top + h * 0.98f)
        close()
    }
    drawPath(path = gripPath, color = gun.darkColor)

    // Grip Grooves
    drawLine(
        color = Color(0x44000000),
        start = Offset(left + w * 0.14f, top + h * 0.65f),
        end = Offset(left + w * 0.36f, top + h * 0.65f),
        strokeWidth = (h * 0.03f).coerceAtLeast(3f)
    )
    drawLine(
        color = Color(0x44000000),
        start = Offset(left + w * 0.12f, top + h * 0.79f),
        end = Offset(left + w * 0.34f, top + h * 0.79f),
        strokeWidth = (h * 0.03f).coerceAtLeast(3f)
    )

    // Trigger Guard & Trigger
    val triggerGuardPath = Path().apply {
        moveTo(left + w * 0.38f, top + h * 0.48f)
        lineTo(left + w * 0.56f, top + h * 0.48f)
        lineTo(left + w * 0.50f, top + h * 0.72f)
        lineTo(left + w * 0.38f, top + h * 0.72f)
    }
    drawPath(
        path = triggerGuardPath,
        color = Color(0xFF0F172A),
        style = Stroke(width = (h * 0.035f).coerceAtLeast(4f))
    )
    drawLine(
        color = Color(0xFFE2E8F0),
        start = Offset(left + w * 0.43f, top + h * 0.50f),
        end = Offset(left + w * 0.40f, top + h * 0.64f),
        strokeWidth = (h * 0.028f).coerceAtLeast(3f)
    )

    // Lower Frame Receiver
    drawRoundRect(
        color = gun.darkColor,
        topLeft = Offset(left + w * 0.08f, top + h * 0.36f),
        size = Size(w * 0.74f, h * 0.14f),
        cornerRadius = CornerRadius(8f, 8f)
    )

    // Recoiling Upper Slide & Barrel
    val slideLeft = left - blowback
    drawRoundRect(
        color = gun.color,
        topLeft = Offset(slideLeft, top + h * 0.11f),
        size = Size(w * 0.86f, h * 0.30f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRect(
        color = gun.color,
        topLeft = Offset(slideLeft + w * 0.86f, top + h * 0.15f),
        size = Size(w * 0.14f, h * 0.22f)
    )
    drawLine(
        color = gun.accentColor,
        start = Offset(slideLeft + w * 0.08f, top + h * 0.15f),
        end = Offset(slideLeft + w * 0.84f, top + h * 0.15f),
        strokeWidth = (h * 0.03f).coerceAtLeast(4f)
    )
    drawRect(
        color = gun.accentColor,
        topLeft = Offset(slideLeft + w * 0.82f, top + h * 0.05f),
        size = Size(w * 0.045f, h * 0.07f)
    )
}

private fun DrawScope.drawMagnumSilhouette(
    gun: GunEntity,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    blowback: Float
) {
    // Heavy Angled Grip
    val gripPath = Path().apply {
        moveTo(left + w * 0.14f, top + h * 0.48f)
        lineTo(left + w * 0.38f, top + h * 0.48f)
        lineTo(left + w * 0.29f, top + h * 0.98f)
        lineTo(left + w * 0.04f, top + h * 0.98f)
        close()
    }
    drawPath(path = gripPath, color = gun.darkColor)

    // Heavy Trigger Guard
    val guardPath = Path().apply {
        moveTo(left + w * 0.34f, top + h * 0.50f)
        lineTo(left + w * 0.52f, top + h * 0.50f)
        lineTo(left + w * 0.47f, top + h * 0.75f)
        lineTo(left + w * 0.34f, top + h * 0.75f)
    }
    drawPath(path = guardPath, color = Color(0xFF1E293B), style = Stroke(width = (h * 0.04f).coerceAtLeast(5f)))

    // Massive Underbarrel Frame
    drawRoundRect(
        color = gun.darkColor,
        topLeft = Offset(left + w * 0.08f, top + h * 0.34f),
        size = Size(w * 0.86f, h * 0.18f),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Long Heavy Ribbed Slide
    val slideLeft = left - blowback
    drawRoundRect(
        color = gun.color,
        topLeft = Offset(slideLeft + w * 0.02f, top + h * 0.08f),
        size = Size(w * 0.94f, h * 0.28f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    // Ventilated Barrel Ports
    for (i in 0..3) {
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(slideLeft + w * (0.48f + i * 0.10f), top + h * 0.13f),
            size = Size(w * 0.055f, h * 0.08f),
            cornerRadius = CornerRadius(3f, 3f)
        )
    }
    // Front Sight & Compensator Lip
    drawRect(
        color = gun.accentColor,
        topLeft = Offset(slideLeft + w * 0.90f, top + h * 0.02f),
        size = Size(w * 0.05f, h * 0.08f)
    )
    drawLine(
        color = gun.accentColor,
        start = Offset(slideLeft + w * 0.06f, top + h * 0.11f),
        end = Offset(slideLeft + w * 0.88f, top + h * 0.11f),
        strokeWidth = (h * 0.028f).coerceAtLeast(3.5f)
    )
}

private fun DrawScope.drawShotgunSilhouette(
    gun: GunEntity,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    blowback: Float
) {
    // Angled Shotgun Stock
    val stockPath = Path().apply {
        moveTo(left + w * 0.02f, top + h * 0.58f)
        lineTo(left + w * 0.26f, top + h * 0.42f)
        lineTo(left + w * 0.34f, top + h * 0.56f)
        lineTo(left + w * 0.10f, top + h * 0.94f)
        lineTo(left + w * 0.0f, top + h * 0.84f)
        close()
    }
    drawPath(path = stockPath, color = gun.darkColor)

    // Trigger Guard
    drawArc(
        color = Color(0xFF0F172A),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(left + w * 0.28f, top + h * 0.44f),
        size = Size(w * 0.14f, h * 0.24f),
        style = Stroke(width = (h * 0.035f).coerceAtLeast(4f))
    )

    // Receiver Body
    drawRoundRect(
        color = gun.color,
        topLeft = Offset(left + w * 0.18f, top + h * 0.24f),
        size = Size(w * 0.34f, h * 0.28f),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Twin Stacked Heavy Barrels + Pump Forend
    val barrelLeft = left - blowback * 0.8f
    drawRoundRect(
        color = gun.color,
        topLeft = Offset(barrelLeft + w * 0.44f, top + h * 0.18f),
        size = Size(w * 0.56f, h * 0.15f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = Color(0xFF334155),
        topLeft = Offset(barrelLeft + w * 0.44f, top + h * 0.35f),
        size = Size(w * 0.50f, h * 0.11f),
        cornerRadius = CornerRadius(5f, 5f)
    )
    // Pump Grip
    drawRoundRect(
        color = gun.accentColor,
        topLeft = Offset(barrelLeft + w * 0.54f, top + h * 0.34f),
        size = Size(w * 0.24f, h * 0.18f),
        cornerRadius = CornerRadius(8f, 8f)
    )
}

private fun DrawScope.drawSmgSilhouette(
    gun: GunEntity,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    blowback: Float
) {
    // Rear Pistol Grip
    val gripPath = Path().apply {
        moveTo(left + w * 0.14f, top + h * 0.46f)
        lineTo(left + w * 0.34f, top + h * 0.46f)
        lineTo(left + w * 0.26f, top + h * 0.92f)
        lineTo(left + w * 0.06f, top + h * 0.92f)
        close()
    }
    drawPath(path = gripPath, color = gun.darkColor)

    // Extended Angled Magazine
    val magPath = Path().apply {
        moveTo(left + w * 0.48f, top + h * 0.48f)
        lineTo(left + w * 0.62f, top + h * 0.48f)
        lineTo(left + w * 0.54f, top + h * 0.98f)
        lineTo(left + w * 0.40f, top + h * 0.98f)
        close()
    }
    drawPath(path = magPath, color = Color(0xFF1E293B))
    drawLine(
        color = gun.accentColor,
        start = Offset(left + w * 0.44f, top + h * 0.88f),
        end = Offset(left + w * 0.52f, top + h * 0.88f),
        strokeWidth = (h * 0.03f).coerceAtLeast(3.5f)
    )

    // Compact SMG Receiver
    val slideLeft = left - blowback * 0.7f
    drawRoundRect(
        color = gun.color,
        topLeft = Offset(slideLeft + w * 0.06f, top + h * 0.18f),
        size = Size(w * 0.76f, h * 0.34f),
        cornerRadius = CornerRadius(12f, 12f)
    )

    // Stubby Shrouded Barrel
    drawRoundRect(
        color = gun.darkColor,
        topLeft = Offset(slideLeft + w * 0.80f, top + h * 0.24f),
        size = Size(w * 0.20f, h * 0.18f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Top Optic Rail & Holographic Sight
    drawRoundRect(
        color = gun.accentColor,
        topLeft = Offset(slideLeft + w * 0.24f, top + h * 0.07f),
        size = Size(w * 0.30f, h * 0.11f),
        cornerRadius = CornerRadius(6f, 6f)
    )
}

private fun DrawScope.drawRailgunSilhouette(
    gun: GunEntity,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
    blowback: Float
) {
    // Rear Grip & Core Capacitor
    val gripPath = Path().apply {
        moveTo(left + w * 0.14f, top + h * 0.50f)
        lineTo(left + w * 0.34f, top + h * 0.50f)
        lineTo(left + w * 0.26f, top + h * 0.96f)
        lineTo(left + w * 0.06f, top + h * 0.96f)
        close()
    }
    drawPath(path = gripPath, color = gun.darkColor)

    // Main Chassis
    drawRoundRect(
        color = gun.darkColor,
        topLeft = Offset(left + w * 0.04f, top + h * 0.20f),
        size = Size(w * 0.40f, h * 0.36f),
        cornerRadius = CornerRadius(12f, 12f)
    )

    // Twin Magnetic Accelerating Rails
    val railLeft = left - blowback * 0.6f
    drawRoundRect(
        color = gun.color,
        topLeft = Offset(railLeft + w * 0.38f, top + h * 0.14f),
        size = Size(w * 0.62f, h * 0.14f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = gun.color,
        topLeft = Offset(railLeft + w * 0.38f, top + h * 0.42f),
        size = Size(w * 0.58f, h * 0.14f),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Glowing Plasma Channel Between Rails
    drawRoundRect(
        color = gun.gunType.bulletGlowColor,
        topLeft = Offset(railLeft + w * 0.36f, top + h * 0.31f),
        size = Size(w * 0.56f, h * 0.08f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawLine(
        color = Color.White,
        start = Offset(railLeft + w * 0.38f, top + h * 0.35f),
        end = Offset(railLeft + w * 0.90f, top + h * 0.35f),
        strokeWidth = (h * 0.025f).coerceAtLeast(3f)
    )
}

private fun DrawScope.drawTarget(target: TargetEntity) {
    val x = target.x
    val y = target.y
    val r = target.radius
    val pulse = (sin(target.pulsePhase) * 0.5f + 0.5f)

    // 1. Top Grenade Pin / Lever & Ring (Proportionally scaled for 2.5x bomb)
    drawRect(
        color = Color(0xFF94A3B8),
        topLeft = Offset(x - r * 0.18f, y - r - r * 0.32f),
        size = Size(r * 0.36f, r * 0.32f)
    )
    drawArc(
        color = Color(0xFFCBD5E1),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(x - r * 0.28f, y - r - r * 0.44f),
        size = Size(r * 0.56f, r * 0.38f),
        style = Stroke(width = (r * 0.08f).coerceAtLeast(4f))
    )

    // 2. Subtle Pulsing Hazard Ring
    drawCircle(
        color = Color(0xFFFF6D00).copy(alpha = 0.14f + pulse * 0.18f),
        radius = r * (1.10f + pulse * 0.06f),
        center = Offset(x, y)
    )

    // 3. Main Bomb/Grenade Sphere Body (Vibrant Yellow)
    drawCircle(
        color = target.color,
        radius = r,
        center = Offset(x, y)
    )

    // 4. Segmented Grid Patterns on Grenade
    drawCircle(
        color = target.darkColor,
        radius = r,
        center = Offset(x, y),
        style = Stroke(width = (r * 0.08f).coerceAtLeast(4f))
    )
    drawLine(
        color = target.darkColor,
        start = Offset(x - r * 0.74f, y),
        end = Offset(x + r * 0.74f, y),
        strokeWidth = (r * 0.065f).coerceAtLeast(3.5f)
    )
    drawLine(
        color = target.darkColor,
        start = Offset(x, y - r * 0.74f),
        end = Offset(x, y + r * 0.74f),
        strokeWidth = (r * 0.065f).coerceAtLeast(3.5f)
    )
    drawCircle(
        color = target.darkColor.copy(alpha = 0.45f),
        radius = r * 0.52f,
        center = Offset(x, y),
        style = Stroke(width = (r * 0.045f).coerceAtLeast(2.5f))
    )

    // 5. Armed Fuse Core Indicator
    drawCircle(
        color = Color(0xFFFF3D00).copy(alpha = 0.55f + pulse * 0.45f),
        radius = r * 0.13f,
        center = Offset(x, y)
    )

    // 6. Gloss shine
    drawCircle(
        color = Color(0x99FFFFFF),
        radius = r * 0.26f,
        center = Offset(x - r * 0.34f, y - r * 0.34f)
    )
}

private fun DrawScope.drawRicochetBullet(bullet: RicochetBullet) {
    // 1. Glowing Laser Tracer Trail
    if (bullet.trailPoints.size > 1) {
        for (i in 0 until bullet.trailPoints.size - 1) {
            val p1 = bullet.trailPoints[i]
            val p2 = bullet.trailPoints[i + 1]
            val alpha = (i.toFloat() / bullet.trailPoints.size).coerceIn(0.12f, 1f)

            drawLine(
                color = bullet.glowColor.copy(alpha = alpha * 0.85f),
                start = p1,
                end = p2,
                strokeWidth = (bullet.radius * 2.2f * alpha).coerceAtLeast(3f),
                cap = StrokeCap.Round
            )
        }
    }

    // 2. Bullet Outer Glow
    drawCircle(
        color = bullet.glowColor,
        radius = bullet.radius * 1.5f,
        center = Offset(bullet.x, bullet.y)
    )
    // 3. Bullet Core
    drawCircle(
        color = Color.White,
        radius = bullet.radius,
        center = Offset(bullet.x, bullet.y)
    )
}

private fun DrawScope.drawDebris(debris: DebrisFragment) {
    val deg = Math.toDegrees(debris.angle.toDouble()).toFloat()
    rotate(degrees = deg, pivot = Offset(debris.x, debris.y)) {
        drawRoundRect(
            color = debris.color.copy(alpha = debris.alpha),
            topLeft = Offset(debris.x - debris.width * 0.5f, debris.y - debris.height * 0.5f),
            size = Size(debris.width, debris.height),
            cornerRadius = CornerRadius(3f, 3f)
        )
    }
}

private fun DrawScope.drawParticle(particle: Particle) {
    drawCircle(
        color = particle.color.copy(alpha = particle.alpha),
        radius = particle.radius,
        center = Offset(particle.x, particle.y)
    )
}
