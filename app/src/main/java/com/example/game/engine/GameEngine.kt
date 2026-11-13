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

    // Core Game Entities
    val playerGun = GunEntity(gunType = initialGunType)
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
    var onSoundEvent: ((GameSoundEvent) -> Unit)? = null

    private var nextBulletId = 1L

    private fun computeArenaScale(): Float {
        val arenaW = (arenaRight - arenaLeft).coerceAtLeast(300f)
        return (arenaW / 960f).coerceIn(0.72f, 1.25f)
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

        // Default frame margins if bounds are not set explicitly
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

        // Apply selected gun type and 2.5x scaled sizes
        playerGun.applyGunType(currentGunType, scale)
        playerGun.recoilKickTimer = 0f
        playerGun.shotsFired = 0
        target.radius = 85f * scale // 2.5x larger bomb target
        target.pulsePhase = 0f

        val halfW = playerGun.width * 0.45f
        val halfH = playerGun.height * 0.45f
        val platH = 22f * scale

        // Levels 1-5 have progressively increasing distance between gun and target.
        // Gun starts near top with natural drop speed and clean spacing for 2.5x entities.
        when (levelNumber) {
            1 -> {
                playerGun.x = (arenaLeft + arenaW * 0.25f).coerceIn(arenaLeft + halfW + 8f, arenaRight - halfW - 8f)
                playerGun.y = (arenaTop + arenaH * 0.22f).coerceIn(arenaTop + halfH + 8f, arenaBottom - halfH - 8f)
                playerGun.vx = 0f
                playerGun.vy = 55f
                playerGun.angle = -0.05f
                playerGun.angularVelocity = 0f
                playerGun.isDestroyed = false

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
                playerGun.isDestroyed = false

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
                playerGun.isDestroyed = false

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
                playerGun.isDestroyed = false

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
                playerGun.isDestroyed = false

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
                // Level 6+: Procedural / Randomized layout with varied positions & obstacles
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
                playerGun.isDestroyed = false

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

        // 1. Spawn Projectile(s) at barrel tip
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
                glowColor = type.bulletGlowColor
            )
            bullet.trailPoints.add(Offset(bullet.x, bullet.y))
            bullets.add(bullet)
        }

        // 2. Apply Mass-Scaled Newtonian Recoil Impulse (Δv = J / m)
        val linearImpulse = type.recoilImpulse / playerGun.mass
        playerGun.vx += backward.x * linearImpulse
        playerGun.vy += backward.y * linearImpulse

        // 3. Apply Realistic Lever-Arm Muzzle-Flip Torque (Δω = τ / I)
        // Barrel axis sits above the Center of Mass (r_y = -lever), recoil acts backward (-J_x),
        // producing natural negative (CCW) muzzle rise relative to the gun's frame.
        val inertia = playerGun.getMomentOfInertia()
        val torqueImpulse = -(type.recoilImpulse / 145f) * type.barrelLeverFraction * 8.2f / inertia
        playerGun.angularVelocity = (playerGun.angularVelocity + torqueImpulse)
            .coerceIn(-maxAngularVelocity, maxAngularVelocity)

        // 4. Spawn Muzzle Flash & Smoke Particles
        spawnMuzzleEffect(barrelTip, forward, type.bulletGlowColor)
        onSoundEvent?.invoke(GameSoundEvent.forGun(type))

        return true
    }

    /**
     * Main Physics Simulation Update Loop (Fixed/Continuous timestep).
     */
    fun update(dt: Float) {
        if (!isInitialized) return
        val clampedDt = dt.coerceIn(0.001f, 0.033f)

        // Animate target fuse pulse
        if (!target.isDestroyed) {
            target.pulsePhase += clampedDt * 5f
        }

        // 1. Update Gun Physics
        if (!playerGun.isDestroyed) {
            if (playerGun.recoilKickTimer > 0f) {
                playerGun.recoilKickTimer = (playerGun.recoilKickTimer - clampedDt).coerceAtLeast(0f)
            }
            updateGunPhysics(clampedDt)
        }

        // 2. Update Bullets Physics & Ricochets
        updateBulletsPhysics(clampedDt)

        // 3. Update Debris Fragments & Particles
        updateDebrisAndParticles(clampedDt)

        // 4. Check Win / Loss Conditions
        checkGameState()
    }

    private fun updateGunPhysics(dt: Float) {
        // Apply Crisp Gravity
        playerGun.vy += gravity * dt

        // Apply Linear Air Drag
        playerGun.vx *= (1f - linearDrag * dt).coerceIn(0f, 1f)
        playerGun.vy *= (1f - (linearDrag * 0.35f) * dt).coerceIn(0f, 1f)

        // Apply Quadratic + Linear Aerodynamic Rotational Damping
        // High spin decays rapidly after a shot; gentle coasting settles smoothly.
        val spinDamping = (angularLinearDrag + angularQuadDrag * abs(playerGun.angularVelocity)) * dt
        playerGun.angularVelocity *= (1f - spinDamping.coerceIn(0f, 0.9f))
        if (abs(playerGun.angularVelocity) < 0.04f) {
            playerGun.angularVelocity = 0f
        }

        // Integrate Position & Orientation
        playerGun.x += playerGun.vx * dt
        playerGun.y += playerGun.vy * dt
        playerGun.angle += playerGun.angularVelocity * dt

        // Normalize Angle between -PI and PI
        while (playerGun.angle > PI) playerGun.angle -= (2 * PI).toFloat()
        while (playerGun.angle < -PI) playerGun.angle += (2 * PI).toFloat()

        // Gun Collision against Arena Boundaries with realistic contact torque
        val halfW = playerGun.width * 0.42f
        val halfH = playerGun.height * 0.42f

        // Left Wall
        if (playerGun.x - halfW < arenaLeft) {
            playerGun.x = arenaLeft + halfW
            if (abs(playerGun.vx) > 95f) onSoundEvent?.invoke(GameSoundEvent.GUN_BOUNCE)
            playerGun.vx = -playerGun.vx * wallRestitution
            playerGun.angularVelocity = (playerGun.angularVelocity * 0.65f - playerGun.vy * 0.0045f)
                .coerceIn(-maxAngularVelocity, maxAngularVelocity)
            spawnWallImpactSparks(arenaLeft, playerGun.y, 1f, 0f)
        }
        // Right Wall
        if (playerGun.x + halfW > arenaRight) {
            playerGun.x = arenaRight - halfW
            if (abs(playerGun.vx) > 95f) onSoundEvent?.invoke(GameSoundEvent.GUN_BOUNCE)
            playerGun.vx = -playerGun.vx * wallRestitution
            playerGun.angularVelocity = (playerGun.angularVelocity * 0.65f + playerGun.vy * 0.0045f)
                .coerceIn(-maxAngularVelocity, maxAngularVelocity)
            spawnWallImpactSparks(arenaRight, playerGun.y, -1f, 0f)
        }
        // Ceiling
        if (playerGun.y - halfH < arenaTop) {
            playerGun.y = arenaTop + halfH
            if (abs(playerGun.vy) > 95f) onSoundEvent?.invoke(GameSoundEvent.GUN_BOUNCE)
            playerGun.vy = -playerGun.vy * wallRestitution
            playerGun.angularVelocity = (playerGun.angularVelocity * 0.65f - playerGun.vx * 0.0045f)
                .coerceIn(-maxAngularVelocity, maxAngularVelocity)
            spawnWallImpactSparks(playerGun.x, arenaTop, 0f, 1f)
        }
        // Floor
        if (playerGun.y + halfH > arenaBottom) {
            playerGun.y = arenaBottom - halfH
            if (abs(playerGun.vy) > 120f) onSoundEvent?.invoke(GameSoundEvent.GUN_BOUNCE)
            playerGun.vy = -playerGun.vy * wallRestitution
            // Ground friction creates rolling torque from horizontal velocity and settles tilted guns
            playerGun.angularVelocity = (playerGun.angularVelocity * 0.58f + playerGun.vx * 0.004f)
                .coerceIn(-maxAngularVelocity, maxAngularVelocity)
            playerGun.vx *= 0.88f
            if (abs(playerGun.vy) < 40f) {
                playerGun.vy = 0f
                // Settle gun angle gently toward nearest flat/upright rest on the floor
                playerGun.angularVelocity *= 0.72f
            }
        }

        // Platform Obstacle Collisions for Gun
        checkPlatformCollisionForGun(platformObstacle, halfW, halfH)
        checkPlatformCollisionForGun(secondPlatformObstacle, halfW, halfH)
    }

    private fun checkPlatformCollisionForGun(plat: PlatformObstacle?, halfW: Float, halfH: Float) {
        if (plat == null) return
        val platLeft = plat.x
        val platRight = plat.x + plat.width
        val platTop = plat.y
        val platBottom = plat.y + plat.height

        if (playerGun.x + halfW > platLeft && playerGun.x - halfW < platRight &&
            playerGun.y + halfH > platTop && playerGun.y - halfH < platBottom
        ) {
            val overlapTop = (playerGun.y + halfH) - platTop
            val overlapBottom = platBottom - (playerGun.y - halfH)
            val overlapLeft = (playerGun.x + halfW) - platLeft
            val overlapRight = platRight - (playerGun.x - halfW)

            val minOverlap = minOf(overlapTop, overlapBottom, overlapLeft, overlapRight)
            when (minOverlap) {
                overlapTop -> {
                    playerGun.y = platTop - halfH
                    playerGun.vy = -playerGun.vy * wallRestitution
                    playerGun.vx *= 0.90f
                }
                overlapBottom -> {
                    playerGun.y = platBottom + halfH
                    playerGun.vy = -playerGun.vy * wallRestitution
                }
                overlapLeft -> {
                    playerGun.x = platLeft - halfW
                    playerGun.vx = -playerGun.vx * wallRestitution
                }
                overlapRight -> {
                    playerGun.x = platRight + halfW
                    playerGun.vx = -playerGun.vx * wallRestitution
                }
            }
        }
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

            // Sub-stepping for precise high-speed ricochet collision
            val steps = 4
            val stepDt = dt / steps
            var hitOccurred = false

            for (step in 0 until steps) {
                // Move bullet
                bullet.x += bullet.vx * stepDt
                bullet.y += bullet.vy * stepDt

                // Keep trail history
                if (step == 0) {
                    bullet.trailPoints.add(Offset(bullet.x, bullet.y))
                    if (bullet.trailPoints.size > 16) {
                        bullet.trailPoints.removeAt(0)
                    }
                }

                // 1. Check Collision with Target
                if (!target.isDestroyed) {
                    val dx = bullet.x - target.x
                    val dy = bullet.y - target.y
                    val distSq = dx * dx + dy * dy
                    val hitDist = bullet.radius + target.radius
                    if (distSq <= hitDist * hitDist) {
                        // TARGET HIT & DESTROYED!
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

                // 2. Check Boundary Collisions (Left, Right, Top, Bottom)
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

            val minO = minOf(overlapTop, overlapBottom, overlapLeft, overlapRight)
            when (minO) {
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

    /**
     * Exact 2D Vector Normal Reflection:
     * v' = (v - 2 * (v · n) * n) * restitution
     */
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
        // Update Debris Fragments
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

        // Update Particle Sparks
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
        if (gameStatus == GameStatus.WON) return

        // Out of bounds lose condition safety
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

    private fun spawnTargetDestructionEffect(x: Float, y: Float) {
        // Spawn Debris Fragments scaled for 2.5x larger bomb
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

        // Spawn Spark/Flame Particles
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
