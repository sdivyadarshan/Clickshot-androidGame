package com.example.game.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.game.audio.GameSoundEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GameEngine(
    val levelNumber: Int = 1,
    val gameMode: GameMode = GameMode.SOLO_PLAY,
    initialGunType: GunType = GunType.PULSE_PISTOL
) {
    var arenaWidth = 800f
    var arenaHeight = 1200f

    // Arena Insets / Playable Bounding Box
    var arenaLeft = 40f
    var arenaTop = 60f
    var arenaRight = 760f
    var arenaBottom = 1140f

    // Physics Constants - Crisp, Responsive Arcade-Realistic Rigid Body Physics
    val gravity = 460f // Natural, responsive downward acceleration px/s^2
    val wallRestitution = 0.52f // Gun bounce elasticity off walls/platforms
    val bulletRestitution = 0.98f // Bullet reflection elasticity
    val linearDrag = 0.42f // Air resistance on translational velocity
    val angularLinearDrag = 1.25f // Linear rotational air damping
    val angularQuadDrag = 0.26f // Quadratic aerodynamic damping for realistic spin decay
    val maxAngularVelocity = 11.5f // Physical cap on spin speed (rad/s)

    // 3-Simultaneous-Hit Elimination Rule Constants (Multiplayer)
    val killsRequiredHits = 3
    val simultaneousHitWindowSec = 1.8f

    // Core Game Entities
    val playerGun = GunEntity(id = "player_gun", gunType = initialGunType)
    val opponentGun = GunEntity(id = "opponent_gun", gunType = initialGunType)
    val target = TargetEntity()
    var platformObstacle: PlatformObstacle? = null
    var secondPlatformObstacle: PlatformObstacle? = null

    val bullets = mutableListOf<RicochetBullet>()
    val debrisList = mutableListOf<DebrisFragment>()
    val particles = mutableListOf<Particle>()

    // Game Status
    var gameStatus: GameStatus = GameStatus.PLAYING
    var shotsFiredCount: Int = 0
    var shotsHitCount: Int = 0
    var isInitialized = false
    var currentGunType: GunType = initialGunType
        private set
    var isOnlineHost: Boolean = true
    var authoritativeLocalPvp: Boolean = false

    var onSoundEvent: ((GameSoundEvent) -> Unit)? = null
    var onLocalShotFired: ((normX: Float, normY: Float, dirAngle: Float, gunType: GunType, pellets: Int, spreadRadians: Float) -> Unit)? = null
    var onLocalHitStateChanged: ((simultaneousHits: Int, isEliminated: Boolean) -> Unit)? = null

    private var nextBulletId = 1L

    private fun computeArenaScale(): Float {
        val arenaW = (arenaRight - arenaLeft).coerceAtLeast(300f)
        return (arenaW / 960f).coerceIn(0.72f, 1.25f)
    }

    fun normalizeX(worldX: Float): Float {
        val w = (arenaRight - arenaLeft).coerceAtLeast(1f)
        return (worldX - arenaLeft) / w
    }

    fun normalizeY(worldY: Float): Float {
        val h = (arenaBottom - arenaTop).coerceAtLeast(1f)
        return (worldY - arenaTop) / h
    }

    fun normalizeVx(vx: Float): Float {
        val w = (arenaRight - arenaLeft).coerceAtLeast(1f)
        return vx / w
    }

    fun normalizeVy(vy: Float): Float {
        val h = (arenaBottom - arenaTop).coerceAtLeast(1f)
        return vy / h
    }

    private fun denormalizeX(normX: Float): Float = arenaLeft + normX * (arenaRight - arenaLeft)
    private fun denormalizeY(normY: Float): Float = arenaTop + normY * (arenaBottom - arenaTop)
    private fun denormalizeVx(normVx: Float): Float = normVx * (arenaRight - arenaLeft)
    private fun denormalizeVy(normVy: Float): Float = normVy * (arenaBottom - arenaTop)

    fun configureOnlineMatch(hostRole: Boolean) {
        isOnlineHost = hostRole
        if (isInitialized) {
            resetLevel()
        }
    }

    fun setGunType(type: GunType) {
        currentGunType = type
        val scale = computeArenaScale()
        playerGun.applyGunType(type, scale)
        // Clamp gun inside bounds after size change
        val halfW = playerGun.width * 0.45f
        val halfH = playerGun.height * 0.45f
        if (isInitialized) {
            playerGun.x = playerGun.x.coerceIn(arenaLeft + halfW + 4f, arenaRight - halfW - 4f)
            playerGun.y = playerGun.y.coerceIn(arenaTop + halfH + 4f, arenaBottom - halfH - 4f)
        }
    }

    fun initializeArena(width: Float, height: Float) {
        arenaWidth = width.coerceAtLeast(320f)
        arenaHeight = height.coerceAtLeast(480f)

        val marginX = arenaWidth * 0.08f
        val marginY = arenaHeight * 0.08f
        arenaLeft = marginX
        arenaRight = arenaWidth - marginX
        arenaTop = marginY
        arenaBottom = arenaHeight - marginY

        resetLevel()
        isInitialized = true
    }

    fun initializeArenaWithBounds(left: Float, top: Float, right: Float, bottom: Float) {
        arenaLeft = left
        arenaTop = top
        arenaRight = right
        arenaBottom = bottom
        arenaWidth = right - left
        arenaHeight = bottom - top

        resetLevel()
        isInitialized = true
    }

    fun resetLevel() {
        bullets.clear()
        debrisList.clear()
        particles.clear()
        gameStatus = GameStatus.PLAYING
        shotsFiredCount = 0
        shotsHitCount = 0

        val arenaW = (arenaRight - arenaLeft).coerceAtLeast(200f)
        val arenaH = (arenaBottom - arenaTop).coerceAtLeast(300f)
        val scale = computeArenaScale()

        playerGun.applyGunType(currentGunType, scale)
        playerGun.recoilKickTimer = 0f
        playerGun.shotsFired = 0
        playerGun.simultaneousHits = 0
        playerGun.hitWindowTimer = 0f
        playerGun.hitFlashTimer = 0f
        playerGun.recoveryFlashTimer = 0f
        playerGun.isDestroyed = false

        opponentGun.applyGunType(GunType.PULSE_PISTOL, scale)
        opponentGun.recoilKickTimer = 0f
        opponentGun.shotsFired = 0
        opponentGun.simultaneousHits = 0
        opponentGun.hitWindowTimer = 0f
        opponentGun.hitFlashTimer = 0f
        opponentGun.recoveryFlashTimer = 0f

        val halfW = playerGun.width * 0.45f
        val halfH = playerGun.height * 0.45f
        val platH = 22f * scale

        if (gameMode == GameMode.DUEL_1V1 || gameMode == GameMode.CUSTOM_LOBBY) {
            // Online 1v1 PvP Arena: Two rival guns, no static bomb target
            target.isDestroyed = true
            opponentGun.isDestroyed = false

            val leftX = (arenaLeft + arenaW * 0.24f).coerceIn(arenaLeft + halfW + 8f, arenaRight - halfW - 8f)
            val rightX = (arenaLeft + arenaW * 0.76f).coerceIn(arenaLeft + halfW + 8f, arenaRight - halfW - 8f)
            val spawnY = (arenaTop + arenaH * 0.22f).coerceIn(arenaTop + halfH + 8f, arenaBottom - halfH - 8f)

            if (isOnlineHost) {
                playerGun.x = leftX
                playerGun.y = spawnY
                playerGun.angle = -0.05f
                opponentGun.x = rightX
                opponentGun.y = spawnY
                opponentGun.angle = (-PI + 0.05f).toFloat()
            } else {
                playerGun.x = rightX
                playerGun.y = spawnY
                playerGun.angle = (-PI + 0.05f).toFloat()
                opponentGun.x = leftX
                opponentGun.y = spawnY
                opponentGun.angle = -0.05f
            }
            playerGun.vx = 0f
            playerGun.vy = 55f
            playerGun.angularVelocity = 0f
            opponentGun.vx = 0f
            opponentGun.vy = 55f
            opponentGun.angularVelocity = 0f

            // Tactical central ricochet bunker for angle shots & simultaneous 3-hit traps
            val midW = arenaW * 0.30f
            val midX = arenaLeft + (arenaW - midW) * 0.5f
            val midY = arenaTop + arenaH * 0.56f
            platformObstacle = PlatformObstacle(midX, midY, midW, platH, Color(0xFF475569))
            secondPlatformObstacle = null
            return
        }

        // Solo mode: opponent gun inactive, bomb target active
        opponentGun.isDestroyed = true
        target.radius = 85f * scale // 2.5x larger bomb target
        target.pulsePhase = 0f

        when (levelNumber) {
            1 -> {
                playerGun.x = (arenaLeft + arenaW * 0.25f).coerceIn(arenaLeft + halfW + 8f, arenaRight - halfW - 8f)
                playerGun.y = (arenaTop + arenaH * 0.22f).coerceIn(arenaTop + halfH + 8f, arenaBottom - halfH - 8f)
                playerGun.vx = 0f
                playerGun.vy = 55f
                playerGun.angle = -0.05f
                playerGun.angularVelocity = 0f

                val targetX = (arenaLeft + arenaW * 0.72f).coerceAtMost(arenaRight - target.radius - 16f)
                val targetY = (arenaTop + arenaH * 0.25f).coerceAtLeast(arenaTop + target.radius + 40f * scale)
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = (arenaW * 0.34f).coerceAtLeast(target.radius * 2.2f)
                val platX = (targetX - platW * 0.5f).coerceIn(arenaLeft + 8f, arenaRight - platW - 8f)
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))
                secondPlatformObstacle = null
            }
            2 -> {
                playerGun.x = (arenaLeft + arenaW * 0.23f).coerceIn(arenaLeft + halfW + 8f, arenaRight - halfW - 8f)
                playerGun.y = (arenaTop + arenaH * 0.20f).coerceIn(arenaTop + halfH + 8f, arenaBottom - halfH - 8f)
                playerGun.vx = 0f
                playerGun.vy = 60f
                playerGun.angle = -0.08f
                playerGun.angularVelocity = 0f

                val targetX = (arenaLeft + arenaW * 0.75f).coerceAtMost(arenaRight - target.radius - 16f)
                val targetY = (arenaTop + arenaH * 0.34f).coerceAtLeast(arenaTop + target.radius + 40f * scale)
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = (arenaW * 0.30f).coerceAtLeast(target.radius * 2.1f)
                val platX = (targetX - platW * 0.5f).coerceIn(arenaLeft + 8f, arenaRight - platW - 8f)
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))
                secondPlatformObstacle = null
            }
            3 -> {
                playerGun.x = (arenaLeft + arenaW * 0.22f).coerceIn(arenaLeft + halfW + 8f, arenaRight - halfW - 8f)
                playerGun.y = (arenaTop + arenaH * 0.19f).coerceIn(arenaTop + halfH + 8f, arenaBottom - halfH - 8f)
                playerGun.vx = 0f
                playerGun.vy = 65f
                playerGun.angle = -0.14f
                playerGun.angularVelocity = 0f

                val targetX = (arenaLeft + arenaW * 0.78f).coerceAtMost(arenaRight - target.radius - 16f)
                val targetY = (arenaTop + arenaH * 0.44f).coerceAtLeast(arenaTop + target.radius + 40f * scale)
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = (arenaW * 0.28f).coerceAtLeast(target.radius * 2.0f)
                val platX = (targetX - platW * 0.5f).coerceIn(arenaLeft + 8f, arenaRight - platW - 8f)
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))
                secondPlatformObstacle = null
            }
            4 -> {
                playerGun.x = (arenaLeft + arenaW * 0.20f).coerceIn(arenaLeft + halfW + 8f, arenaRight - halfW - 8f)
                playerGun.y = (arenaTop + arenaH * 0.18f).coerceIn(arenaTop + halfH + 8f, arenaBottom - halfH - 8f)
                playerGun.vx = 0f
                playerGun.vy = 65f
                playerGun.angle = -0.18f
                playerGun.angularVelocity = 0f

                val targetX = (arenaLeft + arenaW * 0.80f).coerceAtMost(arenaRight - target.radius - 16f)
                val targetY = (arenaTop + arenaH * 0.54f).coerceAtLeast(arenaTop + target.radius + 40f * scale)
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = (arenaW * 0.26f).coerceAtLeast(target.radius * 2.0f)
                val platX = (targetX - platW * 0.5f).coerceIn(arenaLeft + 8f, arenaRight - platW - 8f)
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))
                secondPlatformObstacle = null
            }
            5 -> {
                playerGun.x = (arenaLeft + arenaW * 0.18f).coerceIn(arenaLeft + halfW + 8f, arenaRight - halfW - 8f)
                playerGun.y = (arenaTop + arenaH * 0.18f).coerceIn(arenaTop + halfH + 8f, arenaBottom - halfH - 8f)
                playerGun.vx = 0f
                playerGun.vy = 65f
                playerGun.angle = -0.22f
                playerGun.angularVelocity = 0f

                val targetX = (arenaLeft + arenaW * 0.82f).coerceAtMost(arenaRight - target.radius - 16f)
                val targetY = (arenaTop + arenaH * 0.62f).coerceAtLeast(arenaTop + target.radius + 40f * scale)
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = (arenaW * 0.26f).coerceAtLeast(target.radius * 2.0f)
                val platX = (targetX - platW * 0.5f).coerceIn(arenaLeft + 8f, arenaRight - platW - 8f)
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))
                secondPlatformObstacle = null
            }
            else -> {
                val rand = Random(levelNumber.toLong() * 31L + 7L)
                val spawnLeft = rand.nextBoolean()
                val gunSideX = if (spawnLeft) rand.nextFloat() * 0.10f + 0.18f else rand.nextFloat() * 0.10f + 0.72f
                val targetSideX = if (spawnLeft) rand.nextFloat() * 0.14f + 0.70f else rand.nextFloat() * 0.14f + 0.16f

                playerGun.x = (arenaLeft + arenaW * gunSideX).coerceIn(arenaLeft + halfW + 8f, arenaRight - halfW - 8f)
                playerGun.y = (arenaTop + arenaH * (rand.nextFloat() * 0.08f + 0.16f)).coerceIn(arenaTop + halfH + 8f, arenaBottom - halfH - 8f)
                playerGun.vx = 0f
                playerGun.vy = 65f
                playerGun.angle = if (spawnLeft) -0.15f else (-PI + 0.15f).toFloat()
                playerGun.angularVelocity = 0f

                val targetX = (arenaLeft + arenaW * targetSideX).coerceIn(arenaLeft + target.radius + 16f, arenaRight - target.radius - 16f)
                val targetY = (arenaTop + arenaH * (rand.nextFloat() * 0.34f + 0.30f))
                    .coerceIn(arenaTop + target.radius + 44f * scale, arenaBottom - target.radius - platH - 20f)
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = (arenaW * (rand.nextFloat() * 0.08f + 0.24f)).coerceAtLeast(target.radius * 2.0f)
                val platX = (targetX - platW * 0.5f).coerceIn(arenaLeft + 8f, arenaRight - platW - 8f)
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))

                if (levelNumber >= 7 && rand.nextBoolean()) {
                    val midW = arenaW * 0.22f
                    val midX = arenaLeft + arenaW * 0.39f
                    val midY = arenaTop + arenaH * 0.44f
                    secondPlatformObstacle = PlatformObstacle(midX, midY, midW, platH, Color(0xFF334155))
                } else {
                    secondPlatformObstacle = null
                }
            }
        }
    }

    /**
     * Primary Control Action: Tap to shoot.
     * Fires projectile(s) along the barrel axis, applies mass-scaled Newtonian recoil impulse,
     * and applies realistic muzzle-flip torque divided by the gun's moment of inertia.
     */
    fun tapToShoot(): Boolean {
        if (gameStatus != GameStatus.PLAYING || playerGun.isDestroyed) {
            return false
        }

        val type = playerGun.gunType
        shotsFiredCount++
        playerGun.shotsFired++
        playerGun.recoilKickTimer = 0.14f

        val barrelTip = playerGun.getBarrelTipOffset()
        val forward = playerGun.getForwardVector()
        val backward = playerGun.getBackwardVector()

        val pelletCount = type.pelletsPerShot
        val centerIdx = (pelletCount - 1) / 2f
        for (i in 0 until pelletCount) {
            val angleOffset = (i - centerIdx) * type.spreadRadians
            val shotAngle = playerGun.angle + angleOffset
            val dirX = cos(shotAngle)
            val dirY = sin(shotAngle)

            val bullet = RicochetBullet(
                id = nextBulletId++,
                x = barrelTip.x,
                y = barrelTip.y,
                vx = dirX * type.bulletSpeed,
                vy = dirY * type.bulletSpeed,
                radius = type.bulletRadius,
                bouncesLeft = type.maxBounces,
                maxBounces = type.maxBounces,
                glowColor = type.bulletGlowColor,
                ownerId = "player"
            )
            bullet.trailPoints.add(Offset(bullet.x, bullet.y))
            bullets.add(bullet)
        }

        val linearImpulse = type.recoilImpulse / playerGun.mass
        playerGun.vx += backward.x * linearImpulse
        playerGun.vy += backward.y * linearImpulse

        val inertia = playerGun.getMomentOfInertia()
        val torqueImpulse = -(type.recoilImpulse / 145f) * type.barrelLeverFraction * 8.2f / inertia
        playerGun.angularVelocity = (playerGun.angularVelocity + torqueImpulse)
            .coerceIn(-maxAngularVelocity, maxAngularVelocity)

        spawnMuzzleEffect(barrelTip, forward, type.bulletGlowColor)
        onSoundEvent?.invoke(GameSoundEvent.forGun(type))
        onLocalShotFired?.invoke(
            normalizeX(barrelTip.x),
            normalizeY(barrelTip.y),
            playerGun.angle,
            type,
            pelletCount,
            type.spreadRadians
        )

        return true
    }

    /**
     * Applies synced Firestore pose from remote opponent device anywhere on the internet.
     */
    fun applyRemoteOpponentState(
        normX: Float,
        normY: Float,
        normVx: Float,
        normVy: Float,
        angle: Float,
        angularVelocity: Float,
        gunType: GunType,
        simultaneousHits: Int,
        isEliminated: Boolean
    ) {
        if (!isInitialized) return
        val scale = computeArenaScale()
        if (opponentGun.gunType != gunType) {
            opponentGun.applyGunType(gunType, scale)
        }

        val targetX = denormalizeX(normX)
        val targetY = denormalizeY(normY)
        val dx = targetX - opponentGun.x
        val dy = targetY - opponentGun.y
        if (dx * dx + dy * dy > 160f * 160f) {
            opponentGun.x = targetX
            opponentGun.y = targetY
        } else {
            opponentGun.x += dx * 0.55f
            opponentGun.y += dy * 0.55f
        }
        opponentGun.vx = denormalizeVx(normVx)
        opponentGun.vy = denormalizeVy(normVy)
        opponentGun.angle = angle
        opponentGun.angularVelocity = angularVelocity

        if (!authoritativeLocalPvp) {
            opponentGun.simultaneousHits = simultaneousHits.coerceIn(0, killsRequiredHits)
            if (opponentGun.simultaneousHits > 0) {
                opponentGun.hitWindowTimer = simultaneousHitWindowSec
            }
            if (isEliminated && !opponentGun.isDestroyed) {
                eliminateOpponentGun()
            }
        }
    }

    fun markOpponentEliminatedFromRoomWinner() {
        if (!opponentGun.isDestroyed && gameStatus == GameStatus.PLAYING) {
            opponentGun.simultaneousHits = killsRequiredHits
            eliminateOpponentGun()
        }
    }

    private fun eliminateOpponentGun() {
        opponentGun.isDestroyed = true
        opponentGun.simultaneousHits = killsRequiredHits
        gameStatus = GameStatus.WON
        spawnGunDestructionEffect(opponentGun.x, opponentGun.y, opponentGun.color)
        onSoundEvent?.invoke(GameSoundEvent.ARMOR_BREAK)
        onSoundEvent?.invoke(GameSoundEvent.LEVEL_WON)
    }

    /**
     * Spawns projectiles fired by the remote opponent over Firestore.
     */
    fun spawnRemoteShot(
        normX: Float,
        normY: Float,
        dirAngle: Float,
        gunType: GunType,
        pellets: Int,
        spreadRadians: Float
    ) {
        if (!isInitialized || gameStatus != GameStatus.PLAYING || opponentGun.isDestroyed) return

        val worldX = denormalizeX(normX)
        val worldY = denormalizeY(normY)
        opponentGun.recoilKickTimer = 0.14f
        opponentGun.shotsFired++

        val count = pellets.coerceIn(1, 5)
        val centerIdx = (count - 1) / 2f
        for (i in 0 until count) {
            val angleOffset = (i - centerIdx) * spreadRadians
            val shotAngle = dirAngle + angleOffset
            val bullet = RicochetBullet(
                id = nextBulletId++,
                x = worldX,
                y = worldY,
                vx = cos(shotAngle) * gunType.bulletSpeed,
                vy = sin(shotAngle) * gunType.bulletSpeed,
                radius = gunType.bulletRadius,
                bouncesLeft = gunType.maxBounces,
                maxBounces = gunType.maxBounces,
                color = Color(0xFFFFE4E6),
                glowColor = Color(0xFFFF3366),
                ownerId = "opponent"
            )
            bullet.trailPoints.add(Offset(bullet.x, bullet.y))
            bullets.add(bullet)
        }
        spawnMuzzleEffect(Offset(worldX, worldY), Offset(cos(dirAngle), sin(dirAngle)), Color(0xFFFF3366))
        onSoundEvent?.invoke(GameSoundEvent.forGun(gunType))
    }

    /**
     * Main Physics Simulation Update Loop (Fixed/Continuous timestep).
     */
    fun update(dt: Float) {
        if (!isInitialized) return
        val clampedDt = dt.coerceIn(0.001f, 0.033f)

        if (!target.isDestroyed) {
            target.pulsePhase += clampedDt * 5f
        }

        // 1. Update Local Player Gun Physics & Simultaneous-Hit Window
        if (!playerGun.isDestroyed) {
            updateGunTimers(playerGun, clampedDt) {
                onLocalHitStateChanged?.invoke(0, false)
            }
            updateSingleGunPhysics(playerGun, clampedDt)
        }

        // 2. Update Remote Opponent Gun Physics & Timers (in Multiplayer)
        if ((gameMode == GameMode.DUEL_1V1 || gameMode == GameMode.CUSTOM_LOBBY) && !opponentGun.isDestroyed) {
            updateGunTimers(opponentGun, clampedDt) {}
            updateSingleGunPhysics(opponentGun, clampedDt)
        }

        // 3. Update Bullets Physics & Ricochets
        updateBulletsPhysics(clampedDt)

        // 4. Update Debris Fragments & Particles
        updateDebrisAndParticles(clampedDt)

        // 5. Check Win / Loss Conditions
        checkGameState()
    }

    private fun updateGunTimers(gun: GunEntity, dt: Float, onWindowExpiredReset: () -> Unit) {
        if (gun.recoilKickTimer > 0f) {
            gun.recoilKickTimer = (gun.recoilKickTimer - dt).coerceAtLeast(0f)
        }
        if (gun.hitFlashTimer > 0f) {
            gun.hitFlashTimer = (gun.hitFlashTimer - dt).coerceAtLeast(0f)
        }
        if (gun.recoveryFlashTimer > 0f) {
            gun.recoveryFlashTimer = (gun.recoveryFlashTimer - dt).coerceAtLeast(0f)
        }
        if (gun.hitWindowTimer > 0f && gun.simultaneousHits in 1 until killsRequiredHits) {
            gun.hitWindowTimer = (gun.hitWindowTimer - dt).coerceAtLeast(0f)
            if (gun.hitWindowTimer == 0f) {
                gun.simultaneousHits = 0
                gun.recoveryFlashTimer = 0.45f
                onWindowExpiredReset()
            }
        }
    }

    private fun updateSingleGunPhysics(gun: GunEntity, dt: Float) {
        gun.vy += gravity * dt

        gun.vx *= (1f - linearDrag * dt).coerceIn(0f, 1f)
        gun.vy *= (1f - (linearDrag * 0.35f) * dt).coerceIn(0f, 1f)

        val spinDamping = (angularLinearDrag + angularQuadDrag * abs(gun.angularVelocity)) * dt
        gun.angularVelocity *= (1f - spinDamping.coerceIn(0f, 0.9f))
        if (abs(gun.angularVelocity) < 0.04f) {
            gun.angularVelocity = 0f
        }

        gun.x += gun.vx * dt
        gun.y += gun.vy * dt
        gun.angle += gun.angularVelocity * dt

        while (gun.angle > PI) gun.angle -= (2 * PI).toFloat()
        while (gun.angle < -PI) gun.angle += (2 * PI).toFloat()

        val halfW = gun.width * 0.42f
        val halfH = gun.height * 0.42f
        val emitWallAudio = (gun.id == "player_gun")

        if (gun.x - halfW < arenaLeft) {
            gun.x = arenaLeft + halfW
            if (emitWallAudio && abs(gun.vx) > 95f) onSoundEvent?.invoke(GameSoundEvent.GUN_BOUNCE)
            gun.vx = -gun.vx * wallRestitution
            gun.angularVelocity = (gun.angularVelocity * 0.65f - gun.vy * 0.0045f)
                .coerceIn(-maxAngularVelocity, maxAngularVelocity)
            spawnWallImpactSparks(arenaLeft, gun.y, 1f, 0f)
        }
        if (gun.x + halfW > arenaRight) {
            gun.x = arenaRight - halfW
            if (emitWallAudio && abs(gun.vx) > 95f) onSoundEvent?.invoke(GameSoundEvent.GUN_BOUNCE)
            gun.vx = -gun.vx * wallRestitution
            gun.angularVelocity = (gun.angularVelocity * 0.65f + gun.vy * 0.0045f)
                .coerceIn(-maxAngularVelocity, maxAngularVelocity)
            spawnWallImpactSparks(arenaRight, gun.y, -1f, 0f)
        }
        if (gun.y - halfH < arenaTop) {
            gun.y = arenaTop + halfH
            if (emitWallAudio && abs(gun.vy) > 95f) onSoundEvent?.invoke(GameSoundEvent.GUN_BOUNCE)
            gun.vy = -gun.vy * wallRestitution
            gun.angularVelocity = (gun.angularVelocity * 0.65f - gun.vx * 0.0045f)
                .coerceIn(-maxAngularVelocity, maxAngularVelocity)
            spawnWallImpactSparks(gun.x, arenaTop, 0f, 1f)
        }
        if (gun.y + halfH > arenaBottom) {
            gun.y = arenaBottom - halfH
            if (emitWallAudio && abs(gun.vy) > 120f) onSoundEvent?.invoke(GameSoundEvent.GUN_BOUNCE)
            gun.vy = -gun.vy * wallRestitution
            gun.angularVelocity = (gun.angularVelocity * 0.58f + gun.vx * 0.004f)
                .coerceIn(-maxAngularVelocity, maxAngularVelocity)
            gun.vx *= 0.88f
            if (abs(gun.vy) < 40f) {
                gun.vy = 0f
                gun.angularVelocity *= 0.72f
            }
        }

        checkPlatformCollisionForGun(gun, platformObstacle, halfW, halfH)
        checkPlatformCollisionForGun(gun, secondPlatformObstacle, halfW, halfH)
    }

    private fun checkPlatformCollisionForGun(
        gun: GunEntity,
        plat: PlatformObstacle?,
        halfW: Float,
        halfH: Float
    ) {
        if (plat == null) return
        val platLeft = plat.x
        val platRight = plat.x + plat.width
        val platTop = plat.y
        val platBottom = plat.y + plat.height

        if (gun.x + halfW > platLeft && gun.x - halfW < platRight &&
            gun.y + halfH > platTop && gun.y - halfH < platBottom
        ) {
            val overlapTop = (gun.y + halfH) - platTop
            val overlapBottom = platBottom - (gun.y - halfH)
            val overlapLeft = (gun.x + halfW) - platLeft
            val overlapRight = platRight - (gun.x - halfW)

            when (minOf(overlapTop, overlapBottom, overlapLeft, overlapRight)) {
                overlapTop -> {
                    gun.y = platTop - halfH
                    gun.vy = -gun.vy * wallRestitution
                    gun.vx *= 0.90f
                }
                overlapBottom -> {
                    gun.y = platBottom + halfH
                    gun.vy = -gun.vy * wallRestitution
                }
                overlapLeft -> {
                    gun.x = platLeft - halfW
                    gun.vx = -gun.vx * wallRestitution
                }
                overlapRight -> {
                    gun.x = platRight + halfW
                    gun.vx = -gun.vx * wallRestitution
                }
            }
        }
    }

    private fun isBulletHittingGun(bullet: RicochetBullet, gun: GunEntity): Boolean {
        val dx = bullet.x - gun.x
        val dy = bullet.y - gun.y
        val cosA = cos(-gun.angle)
        val sinA = sin(-gun.angle)
        val localX = dx * cosA - dy * sinA
        val localY = dx * sinA + dy * cosA
        val halfW = gun.width * 0.42f
        val halfH = gun.height * 0.38f
        val closestX = localX.coerceIn(-halfW, halfW)
        val closestY = localY.coerceIn(-halfH, halfH)
        val distX = localX - closestX
        val distY = localY - closestY
        return (distX * distX + distY * distY) <= (bullet.radius * bullet.radius)
    }

    private fun updateBulletsPhysics(dt: Float) {
        val bulletIterator = bullets.iterator()
        while (bulletIterator.hasNext()) {
            val bullet = bulletIterator.next()
            bullet.lifetimeSec -= dt

            if (!bullet.isAlive || bullet.lifetimeSec <= 0f) {
                bulletIterator.remove()
                continue
            }

            val steps = 4
            val stepDt = dt / steps
            var hitOccurred = false

            for (step in 0 until steps) {
                bullet.x += bullet.vx * stepDt
                bullet.y += bullet.vy * stepDt

                if (step == 0) {
                    bullet.trailPoints.add(Offset(bullet.x, bullet.y))
                    if (bullet.trailPoints.size > 16) {
                        bullet.trailPoints.removeAt(0)
                    }
                }

                // 1A. Solo Mode: Check Collision with Bomb Target
                if (!target.isDestroyed) {
                    val dx = bullet.x - target.x
                    val dy = bullet.y - target.y
                    val distSq = dx * dx + dy * dy
                    val hitDist = bullet.radius + target.radius
                    if (distSq <= hitDist * hitDist) {
                        target.isDestroyed = true
                        shotsHitCount++
                        bullet.isAlive = false
                        spawnTargetDestructionEffect(target.x, target.y)
                        onSoundEvent?.invoke(GameSoundEvent.TARGET_EXPLOSION)
                        onSoundEvent?.invoke(GameSoundEvent.LEVEL_WON)
                        gameStatus = GameStatus.WON
                        hitOccurred = true
                        break
                    }
                }

                // 1B. Multiplayer PvP: Check Collision with Rival Guns (3-Simultaneous-Hit Rule!)
                if (gameMode == GameMode.DUEL_1V1 || gameMode == GameMode.CUSTOM_LOBBY) {
                    if (bullet.ownerId == "opponent" && !playerGun.isDestroyed && isBulletHittingGun(bullet, playerGun)) {
                        bullet.isAlive = false
                        handleLocalGunHitByBullet(bullet)
                        hitOccurred = true
                        break
                    } else if (bullet.ownerId == "player" && !opponentGun.isDestroyed && isBulletHittingGun(bullet, opponentGun)) {
                        bullet.isAlive = false
                        shotsHitCount++
                        handleOpponentGunHitLocally(bullet)
                        hitOccurred = true
                        break
                    }
                }

                // 2. Check Boundary Collisions
                if (bullet.x - bullet.radius <= arenaLeft) {
                    bullet.x = arenaLeft + bullet.radius
                    reflectBullet(bullet, normalX = 1f, normalY = 0f)
                } else if (bullet.x + bullet.radius >= arenaRight) {
                    bullet.x = arenaRight - bullet.radius
                    reflectBullet(bullet, normalX = -1f, normalY = 0f)
                }

                if (bullet.y - bullet.radius <= arenaTop) {
                    bullet.y = arenaTop + bullet.radius
                    reflectBullet(bullet, normalX = 0f, normalY = 1f)
                } else if (bullet.y + bullet.radius >= arenaBottom) {
                    bullet.y = arenaBottom - bullet.radius
                    reflectBullet(bullet, normalX = 0f, normalY = -1f)
                }

                // 3. Check Platform Obstacles Ricochet
                checkPlatformRicochet(bullet, platformObstacle)
                checkPlatformRicochet(bullet, secondPlatformObstacle)

                if (!bullet.isAlive) {
                    hitOccurred = true
                    break
                }
            }

            if (hitOccurred && !bullet.isAlive) {
                bulletIterator.remove()
            }
        }
    }

    private fun handleLocalGunHitByBullet(bullet: RicochetBullet) {
        playerGun.vx += bullet.vx * 0.16f
        playerGun.vy += bullet.vy * 0.14f
        playerGun.angularVelocity = (playerGun.angularVelocity + (Random.nextFloat() - 0.5f) * 6f)
            .coerceIn(-maxAngularVelocity, maxAngularVelocity)

        playerGun.simultaneousHits = (playerGun.simultaneousHits + 1).coerceAtMost(killsRequiredHits)
        playerGun.hitWindowTimer = simultaneousHitWindowSec
        playerGun.hitFlashTimer = 0.24f

        val killed = playerGun.simultaneousHits >= killsRequiredHits
        spawnArmorHitSparks(bullet.x, bullet.y, isBreak = killed)

        if (killed) {
            playerGun.isDestroyed = true
            gameStatus = GameStatus.LOST
            spawnGunDestructionEffect(playerGun.x, playerGun.y, playerGun.color)
            onSoundEvent?.invoke(GameSoundEvent.ARMOR_BREAK)
            onSoundEvent?.invoke(GameSoundEvent.LEVEL_LOST)
            onLocalHitStateChanged?.invoke(killsRequiredHits, true)
        } else {
            onSoundEvent?.invoke(GameSoundEvent.ARMOR_HIT)
            onLocalHitStateChanged?.invoke(playerGun.simultaneousHits, false)
        }
    }

    private fun handleOpponentGunHitLocally(bullet: RicochetBullet) {
        opponentGun.vx += bullet.vx * 0.12f
        opponentGun.vy += bullet.vy * 0.10f
        opponentGun.hitFlashTimer = 0.24f

        if (authoritativeLocalPvp) {
            opponentGun.simultaneousHits = (opponentGun.simultaneousHits + 1).coerceAtMost(killsRequiredHits)
            opponentGun.hitWindowTimer = simultaneousHitWindowSec
            val killed = opponentGun.simultaneousHits >= killsRequiredHits
            spawnArmorHitSparks(bullet.x, bullet.y, isBreak = killed)
            if (killed) {
                eliminateOpponentGun()
            } else {
                onSoundEvent?.invoke(GameSoundEvent.ARMOR_HIT)
            }
        } else {
            // Immediate visual hit feedback; remote defender commits final 3-hit elimination
            spawnArmorHitSparks(bullet.x, bullet.y, isBreak = false)
            onSoundEvent?.invoke(GameSoundEvent.ARMOR_HIT)
        }
    }

    private fun checkPlatformRicochet(bullet: RicochetBullet, plat: PlatformObstacle?) {
        if (plat == null || !bullet.isAlive) return
        if (bullet.x + bullet.radius >= plat.x &&
            bullet.x - bullet.radius <= plat.x + plat.width &&
            bullet.y + bullet.radius >= plat.y &&
            bullet.y - bullet.radius <= plat.y + plat.height
        ) {
            val overlapTop = abs((bullet.y + bullet.radius) - plat.y)
            val overlapBottom = abs((plat.y + plat.height) - (bullet.y - bullet.radius))
            val overlapLeft = abs((bullet.x + bullet.radius) - plat.x)
            val overlapRight = abs((plat.x + plat.width) - (bullet.x - bullet.radius))

            when (minOf(overlapTop, overlapBottom, overlapLeft, overlapRight)) {
                overlapTop -> {
                    bullet.y = plat.y - bullet.radius
                    reflectBullet(bullet, 0f, -1f)
                }
                overlapBottom -> {
                    bullet.y = plat.y + plat.height + bullet.radius
                    reflectBullet(bullet, 0f, 1f)
                }
                overlapLeft -> {
                    bullet.x = plat.x - bullet.radius
                    reflectBullet(bullet, -1f, 0f)
                }
                overlapRight -> {
                    bullet.x = plat.x + plat.width + bullet.radius
                    reflectBullet(bullet, 1f, 0f)
                }
            }
        }
    }

    private fun reflectBullet(bullet: RicochetBullet, normalX: Float, normalY: Float) {
        val dot = bullet.vx * normalX + bullet.vy * normalY
        bullet.vx = (bullet.vx - 2f * dot * normalX) * bulletRestitution
        bullet.vy = (bullet.vy - 2f * dot * normalY) * bulletRestitution

        bullet.bouncesLeft--
        spawnWallImpactSparks(bullet.x, bullet.y, normalX, normalY)
        onSoundEvent?.invoke(GameSoundEvent.BULLET_RICOCHET)

        if (bullet.bouncesLeft <= 0) {
            bullet.isAlive = false
        }
    }

    private fun updateDebrisAndParticles(dt: Float) {
        val debrisIterator = debrisList.iterator()
        while (debrisIterator.hasNext()) {
            val d = debrisIterator.next()
            d.life -= dt
            if (d.life <= 0f) {
                debrisIterator.remove()
                continue
            }
            d.vy += gravity * dt * 0.6f
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.angle += d.angularVelocity * dt
            d.alpha = (d.life / d.maxLife).coerceIn(0f, 1f)
        }

        val particleIterator = particles.iterator()
        while (particleIterator.hasNext()) {
            val p = particleIterator.next()
            p.life -= dt
            if (p.life <= 0f) {
                particleIterator.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
        }
    }

    private fun checkGameState() {
        if (gameStatus != GameStatus.PLAYING) return
        if (gameMode == GameMode.DUEL_1V1 || gameMode == GameMode.CUSTOM_LOBBY) {
            // In PvP, guns stay enclosed inside the chamber; only 3 simultaneous hits can eliminate!
            return
        }

        if (playerGun.y > arenaBottom + 120f || playerGun.y < arenaTop - 200f) {
            gameStatus = GameStatus.LOST
        }
    }

    // --- Particle and Effect Spawners ---

    private fun spawnMuzzleEffect(origin: Offset, forward: Offset, tint: Color = Color(0xFFFFD54F)) {
        val count = 14
        for (i in 0 until count) {
            val spreadAngle = (Random.nextFloat() - 0.5f) * 0.9f
            val speed = Random.nextFloat() * 280f + 100f
            val cosS = cos(spreadAngle)
            val sinS = sin(spreadAngle)
            val dirX = forward.x * cosS - forward.y * sinS
            val dirY = forward.x * sinS + forward.y * cosS

            particles.add(
                Particle(
                    x = origin.x,
                    y = origin.y,
                    vx = dirX * speed,
                    vy = dirY * speed,
                    color = if (i % 2 == 0) tint else Color(0xFFFFD54F),
                    radius = Random.nextFloat() * 7f + 3.5f,
                    life = 0.34f,
                    maxLife = 0.34f
                )
            )
        }
    }

    private fun spawnArmorHitSparks(x: Float, y: Float, isBreak: Boolean) {
        val count = if (isBreak) 24 else 14
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = Random.nextFloat() * 340f + 120f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = if (isBreak) Color(0xFFFF1744) else if (i % 2 == 0) Color(0xFFFF9100) else Color(0xFFFFFFFF),
                    radius = Random.nextFloat() * 6f + 3f,
                    life = 0.42f,
                    maxLife = 0.42f
                )
            )
        }
    }

    private fun spawnWallImpactSparks(x: Float, y: Float, normalX: Float, normalY: Float) {
        val count = 8
        for (i in 0 until count) {
            val spread = (Random.nextFloat() - 0.5f) * 1.6f
            val speed = Random.nextFloat() * 260f + 100f
            val cosS = cos(spread)
            val sinS = sin(spread)
            val dirX = normalX * cosS - normalY * sinS
            val dirY = normalX * sinS + normalY * cosS

            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = dirX * speed,
                    vy = dirY * speed,
                    color = Color(0xFF00E5FF),
                    radius = Random.nextFloat() * 5.5f + 2.5f,
                    life = 0.38f,
                    maxLife = 0.38f
                )
            )
        }
    }

    private fun spawnGunDestructionEffect(x: Float, y: Float, tint: Color) {
        val debrisCount = 22
        for (i in 0 until debrisCount) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = Random.nextFloat() * 540f + 180f
            debrisList.add(
                DebrisFragment(
                    x = x + (Random.nextFloat() - 0.5f) * 50f,
                    y = y + (Random.nextFloat() - 0.5f) * 50f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 180f,
                    angle = Random.nextFloat() * 6.28f,
                    angularVelocity = (Random.nextFloat() - 0.5f) * 20f,
                    width = Random.nextFloat() * 28f + 16f,
                    height = Random.nextFloat() * 20f + 10f,
                    color = if (i % 2 == 0) tint else Color(0xFFFF5252),
                    life = 1.6f,
                    maxLife = 1.6f
                )
            )
        }
        val particleCount = 44
        for (i in 0 until particleCount) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = Random.nextFloat() * 560f + 90f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = when (i % 3) {
                        0 -> Color(0xFFFF1744)
                        1 -> Color(0xFFFFD600)
                        else -> Color.White
                    },
                    radius = Random.nextFloat() * 9f + 4f,
                    life = Random.nextFloat() * 0.55f + 0.35f,
                    maxLife = 0.75f
                )
            )
        }
    }

    private fun spawnTargetDestructionEffect(x: Float, y: Float) {
        val debrisCount = 20
        for (i in 0 until debrisCount) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = Random.nextFloat() * 520f + 160f
            debrisList.add(
                DebrisFragment(
                    x = x + (Random.nextFloat() - 0.5f) * 45f,
                    y = y + (Random.nextFloat() - 0.5f) * 45f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 180f,
                    angle = Random.nextFloat() * 6.28f,
                    angularVelocity = (Random.nextFloat() - 0.5f) * 18f,
                    width = Random.nextFloat() * 26f + 16f,
                    height = Random.nextFloat() * 22f + 12f,
                    color = if (i % 2 == 0) Color(0xFFFFD600) else Color(0xFFFF9100),
                    life = 1.6f,
                    maxLife = 1.6f
                )
            )
        }

        val particleCount = 44
        for (i in 0 until particleCount) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = Random.nextFloat() * 560f + 90f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = when (i % 3) {
                        0 -> Color(0xFFFFD600)
                        1 -> Color(0xFFFF3D00)
                        else -> Color(0xFFFF9E80)
                    },
                    radius = Random.nextFloat() * 9f + 4f,
                    life = Random.nextFloat() * 0.55f + 0.35f,
                    maxLife = 0.75f
                )
            )
        }
    }
}
