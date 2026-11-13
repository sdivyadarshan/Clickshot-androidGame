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
 * Distinct arsenal weapons with unique geometry, mass, recoil physics, and projectile traits.
 * Base dimensions are ~2.5x scaled for bold close-up arena readability.
 */
enum class GunType(
    val displayName: String,
    val tagline: String,
    val baseWidth: Float,
    val baseHeight: Float,
    val mass: Float,
    val recoilImpulse: Float,
    val barrelLeverFraction: Float,
    val bulletSpeed: Float,
    val bulletRadius: Float,
    val maxBounces: Int,
    val pelletsPerShot: Int,
    val spreadRadians: Float,
    val barrelForwardFraction: Float,
    val barrelUpwardFraction: Float,
    val primaryColor: Color,
    val darkColor: Color,
    val accentColor: Color,
    val bulletGlowColor: Color
) {
    PULSE_PISTOL(
        displayName = "Pulse",
        tagline = "Balanced semi-auto",
        baseWidth = 275f,
        baseHeight = 175f,
        mass = 1.0f,
        recoilImpulse = 540f,
        barrelLeverFraction = 0.24f,
        bulletSpeed = 2200f,
        bulletRadius = 10f,
        maxBounces = 4,
        pelletsPerShot = 1,
        spreadRadians = 0f,
        barrelForwardFraction = 0.49f,
        barrelUpwardFraction = -0.18f,
        primaryColor = Color(0xFF00A2FF),
        darkColor = Color(0xFF0064A8),
        accentColor = Color(0xFF67D4FF),
        bulletGlowColor = Color(0xFF00E5FF)
    ),
    HEAVY_DEAGLE(
        displayName = "Magnum",
        tagline = "Heavy kick, 5 bounces",
        baseWidth = 298f,
        baseHeight = 185f,
        mass = 1.45f,
        recoilImpulse = 860f,
        barrelLeverFraction = 0.27f,
        bulletSpeed = 2750f,
        bulletRadius = 12.5f,
        maxBounces = 5,
        pelletsPerShot = 1,
        spreadRadians = 0f,
        barrelForwardFraction = 0.50f,
        barrelUpwardFraction = -0.20f,
        primaryColor = Color(0xFFF59E0B),
        darkColor = Color(0xFF92400E),
        accentColor = Color(0xFFFDE68A),
        bulletGlowColor = Color(0xFFFBBF24)
    ),
    SCATTER_SHOTGUN(
        displayName = "Scatter",
        tagline = "3-pellet spread burst",
        baseWidth = 315f,
        baseHeight = 172f,
        mass = 1.30f,
        recoilImpulse = 790f,
        barrelLeverFraction = 0.22f,
        bulletSpeed = 2000f,
        bulletRadius = 8.5f,
        maxBounces = 3,
        pelletsPerShot = 3,
        spreadRadians = 0.13f,
        barrelForwardFraction = 0.50f,
        barrelUpwardFraction = -0.15f,
        primaryColor = Color(0xFF10B981),
        darkColor = Color(0xFF065F46),
        accentColor = Color(0xFF6EE7B7),
        bulletGlowColor = Color(0xFF34D399)
    ),
    VECTOR_SMG(
        displayName = "Viper",
        tagline = "Light frame, agile spin",
        baseWidth = 252f,
        baseHeight = 168f,
        mass = 0.78f,
        recoilImpulse = 440f,
        barrelLeverFraction = 0.25f,
        bulletSpeed = 2350f,
        bulletRadius = 9f,
        maxBounces = 4,
        pelletsPerShot = 1,
        spreadRadians = 0f,
        barrelForwardFraction = 0.48f,
        barrelUpwardFraction = -0.17f,
        primaryColor = Color(0xFFF43F5E),
        darkColor = Color(0xFF9F1239),
        accentColor = Color(0xFFFDA4AF),
        bulletGlowColor = Color(0xFFFB7185)
    ),
    RAIL_CANNON(
        displayName = "Railgun",
        tagline = "6-bounce hyper plasma",
        baseWidth = 328f,
        baseHeight = 178f,
        mass = 1.60f,
        recoilImpulse = 920f,
        barrelLeverFraction = 0.19f,
        bulletSpeed = 3100f,
        bulletRadius = 11.5f,
        maxBounces = 6,
        pelletsPerShot = 1,
        spreadRadians = 0f,
        barrelForwardFraction = 0.51f,
        barrelUpwardFraction = -0.12f,
        primaryColor = Color(0xFF8B5CF6),
        darkColor = Color(0xFF4C1D95),
        accentColor = Color(0xFFDDD6FE),
        bulletGlowColor = Color(0xFFC4B5FD)
    )
}

/**
 * 2D Physics Body representing the Player's Gun.
 * Movement and orientation emerge from Newtonian recoil impulse, lever-arm torque,
 * moment of inertia, quadratic air damping, gravity, and surface contact restitution.
 */
data class GunEntity(
    val id: String = "player_gun",
    var gunType: GunType = GunType.PULSE_PISTOL,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var angle: Float = 0f, // in radians (0 = pointing right)
    var angularVelocity: Float = 0f, // in radians per second
    var width: Float = 275f, // 2.5x scaled
    var height: Float = 175f, // 2.5x scaled
    var mass: Float = 1.0f,
    var color: Color = GunType.PULSE_PISTOL.primaryColor,
    val darkColorDefault: Color = GunType.PULSE_PISTOL.darkColor,
    var darkColor: Color = GunType.PULSE_PISTOL.darkColor,
    var accentColor: Color = GunType.PULSE_PISTOL.accentColor,
    var isDestroyed: Boolean = false,
    var shotsFired: Int = 0,
    var recoilKickTimer: Float = 0f // Visual slide blowback timer (seconds)
) {
    fun applyGunType(type: GunType, arenaScale: Float = 1f) {
        gunType = type
        width = type.baseWidth * arenaScale
        height = type.baseHeight * arenaScale
        mass = type.mass
        color = type.primaryColor
        darkColor = type.darkColor
        accentColor = type.accentColor
    }

    /**
     * Normalized 2D rigid-body moment of inertia I = m * ((w/wRef)^2 + (h/hRef)^2) / 2
     */
    fun getMomentOfInertia(): Float {
        val wNorm = width / 275f
        val hNorm = height / 175f
        return (mass * (wNorm * wNorm + hNorm * hNorm) * 0.5f).coerceAtLeast(0.4f)
    }

    /**
     * Calculates the world-space coordinate of the barrel muzzle where bullets spawn.
     */
    fun getBarrelTipOffset(): Offset {
        val barrelForwardOffset = width * gunType.barrelForwardFraction
        val barrelUpwardOffset = height * gunType.barrelUpwardFraction
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
    val radius: Float = 10f,
    var bouncesLeft: Int = 4,
    val maxBounces: Int = 4,
    val color: Color = Color(0xFFE0F7FA),
    val glowColor: Color = Color(0xFF00E5FF),
    val trailPoints: MutableList<Offset> = mutableListOf(),
    var isAlive: Boolean = true,
    var lifetimeSec: Float = 4.0f
)

/**
 * Target object to destroy (2.5x scaled yellow bomb/grenade).
 */
data class TargetEntity(
    val id: Long = 1L,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var radius: Float = 85f, // 2.5x original 34f
    val color: Color = Color(0xFFFFD600),
    val accentColor: Color = Color(0xFFE5B800),
    val darkColor: Color = Color(0xFFB38600),
    var isDestroyed: Boolean = false,
    var pulsePhase: Float = 0f
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

