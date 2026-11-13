package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.engine.DebrisFragment
import com.example.game.engine.GameEngine
import com.example.game.engine.GameStatus
import com.example.game.engine.GunEntity
import com.example.game.engine.Particle
import com.example.game.engine.PlatformObstacle
import com.example.game.engine.RicochetBullet
import com.example.game.engine.TargetEntity

@Composable
fun ArenaGameScreen(
    engine: GameEngine?,
    gameTick: Long,
    onInitArena: (Float, Float) -> Unit = { _, _ -> },
    onInitArenaBounds: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onTapScreen: () -> Unit,
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

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E222A)) // Sleek dark outer background
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        // Calculate focused, centered arena playfield (Close-up viewpoint matching reference)
        val paddingH = with(density) { 16.dp.toPx() }
        val headerH = with(density) { 72.dp.toPx() }
        val bottomPadding = with(density) { 24.dp.toPx() }

        val availableW = (screenW - paddingH * 2).coerceAtLeast(200f)
        val availableH = (screenH - headerH - bottomPadding).coerceAtLeast(300f)

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
                .pointerInput(engine) {
                    detectTapGestures(
                        onTap = {
                            if (engine.gameStatus == GameStatus.PLAYING) {
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

        // Minimal Header Bar (Level Indicator, Restart Button, Sound Toggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Level ${engine.levelNumber}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFF1F5F9),
                modifier = Modifier.testTag("level_indicator")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = { onRestartLevel() },
                    modifier = Modifier.testTag("reset_level_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restart Level",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = { /* Sound toggle */ },
                    modifier = Modifier.testTag("sound_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Sound",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Idle Tap Hint
        if (engine.shotsFiredCount == 0 && engine.gameStatus == GameStatus.PLAYING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "TAP ANYWHERE TO SHOOT",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = Color(0xB3FFFFFF)
                )
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

    rotate(degrees = angleDeg, pivot = Offset(gun.x, gun.y)) {
        val w = gun.width
        val h = gun.height
        val left = gun.x - w * 0.5f
        val top = gun.y - h * 0.5f

        // 1. Pistol Grip / Handle (Dark blue angled grip)
        val gripPath = Path().apply {
            moveTo(left + w * 0.18f, top + h * 0.48f)
            lineTo(left + w * 0.44f, top + h * 0.48f)
            lineTo(left + w * 0.35f, top + h * 0.98f)
            lineTo(left + w * 0.08f, top + h * 0.98f)
            close()
        }
        drawPath(path = gripPath, color = gun.darkColor)

        // 2. Grip Texture Grooves
        drawLine(
            color = Color(0x33000000),
            start = Offset(left + w * 0.15f, top + h * 0.65f),
            end = Offset(left + w * 0.36f, top + h * 0.65f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = Color(0x33000000),
            start = Offset(left + w * 0.13f, top + h * 0.78f),
            end = Offset(left + w * 0.33f, top + h * 0.78f),
            strokeWidth = 2.dp.toPx()
        )

        // 3. Trigger Guard
        val triggerGuardPath = Path().apply {
            moveTo(left + w * 0.38f, top + h * 0.50f)
            lineTo(left + w * 0.56f, top + h * 0.50f)
            lineTo(left + w * 0.50f, top + h * 0.72f)
            lineTo(left + w * 0.38f, top + h * 0.72f)
        }
        drawPath(
            path = triggerGuardPath,
            color = Color(0xFF0F172A),
            style = Stroke(width = 2.5.dp.toPx())
        )

        // 4. Trigger
        drawLine(
            color = Color(0xFFE2E8F0),
            start = Offset(left + w * 0.42f, top + h * 0.52f),
            end = Offset(left + w * 0.40f, top + h * 0.64f),
            strokeWidth = 2.dp.toPx()
        )

        // 5. Main Gun Slide & Barrel Receiver (Vibrant Blue Body)
        drawRoundRect(
            color = gun.color,
            topLeft = Offset(left, top + h * 0.12f),
            size = Size(w * 0.86f, h * 0.38f),
            cornerRadius = CornerRadius(5f, 5f)
        )

        // 6. Front Muzzle Extension
        drawRect(
            color = gun.color,
            topLeft = Offset(left + w * 0.86f, top + h * 0.18f),
            size = Size(w * 0.14f, h * 0.26f)
        )

        // 7. Slide Top Highlight & Front Sight
        drawLine(
            color = gun.accentColor,
            start = Offset(left + w * 0.08f, top + h * 0.14f),
            end = Offset(left + w * 0.84f, top + h * 0.14f),
            strokeWidth = 2.5.dp.toPx()
        )
        drawRect(
            color = gun.accentColor,
            topLeft = Offset(left + w * 0.82f, top + h * 0.08f),
            size = Size(6f, 6f)
        )
    }
}

private fun DrawScope.drawTarget(target: TargetEntity) {
    val x = target.x
    val y = target.y
    val r = target.radius

    // 1. Top Grenade Pin / Lever (Metallic Gray)
    drawRect(
        color = Color(0xFF94A3B8),
        topLeft = Offset(x - 6f, y - r - 12f),
        size = Size(12f, 12f)
    )
    drawArc(
        color = Color(0xFFCBD5E1),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(x - 10f, y - r - 16f),
        size = Size(20f, 14f),
        style = Stroke(width = 2.5.dp.toPx())
    )

    // 2. Main Grenade Sphere Body (Vibrant Yellow)
    drawCircle(
        color = target.color,
        radius = r,
        center = Offset(x, y)
    )

    // 3. Segmented Grid Patterns on Grenade
    drawCircle(
        color = target.darkColor,
        radius = r,
        center = Offset(x, y),
        style = Stroke(width = 3.dp.toPx())
    )
    drawLine(
        color = target.darkColor,
        start = Offset(x - r * 0.72f, y),
        end = Offset(x + r * 0.72f, y),
        strokeWidth = 2.5.dp.toPx()
    )
    drawLine(
        color = target.darkColor,
        start = Offset(x, y - r * 0.72f),
        end = Offset(x, y + r * 0.72f),
        strokeWidth = 2.5.dp.toPx()
    )

    // 4. Gloss shine
    drawCircle(
        color = Color(0x99FFFFFF),
        radius = r * 0.28f,
        center = Offset(x - r * 0.32f, y - r * 0.32f)
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
