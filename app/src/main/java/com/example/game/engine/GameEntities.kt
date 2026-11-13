package com.example.game.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

enum class GameStatus {
    PLAYING,
    WON,
    LOST
}

enum class Team {
    BLUE, RED
}

enum class GameMode(val title: String, val subtitle: String, val maxPlayers: Int) {
    SOLO_PLAY("Play Solo", "Physics target shooting and ricochet action", 1),
    DUEL_1V1("1v1 Duel", "Coming soon in multiplayer phase", 2),
    TEAM_2V2("2v2 Team", "Coming soon in multiplayer phase", 4),
    CUSTOM_LOBBY("Custom Lobby", "Coming soon in multiplayer phase", 4)
}

enum class DifficultyTier(val displayName: String, val color: Color, val multiplier: Float) {
    EASY("Level 1", Color(0xFF10B981), 1.0f),
    NORMAL("Level 2", Color(0xFF00B0FF), 1.25f),
    HARD("Level 3", Color(0xFFFFB300), 1.6f),
    EXPERT("Level 4", Color(0xFFFF7043), 2.0f),
    NIGHTMARE("Level 5", Color(0xFFF43F5E), 2.5f),
    CHAOS("Level 6", Color(0xFFD50000), 3.0f)
}

/**
 * 2D Physics Body representing the Player's Gun.
 * Movement and orientation emerge entirely from recoil impulse, angular torque, gravity, and wall restitution.
 */
data class GunEntity(
    val id: String = "player_gun",
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var angle: Float = 0f, // in radians (0 = pointing right)
    var angularVelocity: Float = 0f, // in radians per second
    val width: Float = 110f,
    val height: Float = 70f,
    val mass: Float = 1.0f,
    val color: Color = Color(0xFF00A2FF),
    val darkColor: Color = Color(0xFF0070B8),
    val accentColor: Color = Color(0xFF67D4FF),
    var isDestroyed: Boolean = false,
    var shotsFired: Int = 0
) {
    /**
     * Calculates the world-space coordinate of the barrel muzzle where bullets spawn.
     */
    fun getBarrelTipOffset(): Offset {
        val barrelForwardOffset = width * 0.48f
        val barrelUpwardOffset = -height * 0.16f
        val cosA = cos(angle)
        val sinA = sin(angle)
        val worldX = x + (barrelForwardOffset * cosA - barrelUpwardOffset * sinA)
        val worldY = y + (barrelForwardOffset * sinA + barrelUpwardOffset * cosA)
        return Offset(worldX, worldY)
    }

    /**
     * Forward firing direction vector.
     */
    fun getForwardVector(): Offset {
        return Offset(cos(angle), sin(angle))
    }

    /**
     * Backward recoil direction vector.
     */
    fun getBackwardVector(): Offset {
        return Offset(-cos(angle), -sin(angle))
    }
}

/**
 * High-velocity projectile with ricochet reflection physics.
 */
data class RicochetBullet(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float = 6.5f,
    var bouncesLeft: Int = 4,
    val maxBounces: Int = 4,
    val color: Color = Color(0xFFE0F7FA),
    val glowColor: Color = Color(0xFF00E5FF),
    val trailPoints: MutableList<Offset> = mutableListOf(),
    var isAlive: Boolean = true,
    var lifetimeSec: Float = 4.0f
)

/**
 * Target object to destroy (e.g., yellow grenade, bomb, or target).
 */
data class TargetEntity(
    val id: Long = 1L,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val radius: Float = 34f,
    val color: Color = Color(0xFFFFD600),
    val accentColor: Color = Color(0xFFE5B800),
    val darkColor: Color = Color(0xFFB38600),
    var isDestroyed: Boolean = false
)

/**
 * Solid arena obstacle/shelf platform.
 */
data class PlatformObstacle(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val color: Color = Color(0xFF334155)
)

/**
 * Visual debris piece spawned when target or weapon is shattered.
 */
data class DebrisFragment(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angle: Float = 0f,
    var angularVelocity: Float = 0f,
    val width: Float,
    val height: Float,
    val color: Color,
    var alpha: Float = 1f,
    var life: Float = 1.2f,
    val maxLife: Float = 1.2f
)

/**
 * Particle spark for recoil smoke, wall bounces, and impact bursts.
 */
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var radius: Float,
    var alpha: Float = 1f,
    var life: Float = 0.5f,
    val maxLife: Float = 0.5f
)

/**
 * Minimal arena color theme
 */
enum class ArenaTheme(val title: String, val primaryColor: Color, val accentColor: Color) {
    MINIMAL_SLATE("Slate Arena", Color(0xFF00A2FF), Color(0xFFFFD600)),
    GRAPHITE("Graphite Dark", Color(0xFF38BDF8), Color(0xFFF59E0B)),
    CONCRETE("Concrete Vault", Color(0xFF60A5FA), Color(0xFFFBBF24))
}

