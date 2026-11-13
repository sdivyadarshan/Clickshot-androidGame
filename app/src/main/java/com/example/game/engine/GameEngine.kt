package com.example.game.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GameEngine(
    val levelNumber: Int = 1,
    val gameMode: GameMode = GameMode.SOLO_PLAY
) {
    var arenaWidth = 800f
    var arenaHeight = 1200f

    // Arena Insets / Playable Bounding Box
    var arenaLeft = 40f
    var arenaTop = 60f
    var arenaRight = 760f
    var arenaBottom = 1140f

    // Physics Constants - Ultra Slow, Floaty Gravity & Gentle Predictable Recoil
    val gravity = 160f // Very slow, gentle floaty downward acceleration px/s^2
    val gunRecoilForce = 340f // Smooth impulse speed imparted to gun backwards
    val gunRecoilTorque = 2.4f // Gentle angular torque impulse (rad/s) so gun doesn't spin uncontrollably
    val bulletSpeed = 1900f // High velocity laser-like projectile px/s
    val wallRestitution = 0.55f // Gun bounce elasticity off walls
    val bulletRestitution = 0.98f // Bullet reflection elasticity
    val linearDrag = 0.35f // Air resistance for graceful slow floating
    val angularDrag = 0.85f // High rotational damping so gun settles smoothly

    // Core Game Entities
    val playerGun = GunEntity()
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

    private var nextBulletId = 1L

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

        // Levels 1-5 have progressively increasing distance between gun and target.
        // The gun always drops slowly from top with gentle vy downward.
        when (levelNumber) {
            1 -> {
                // Level 1: Very close distance, extremely forgiving & easy!
                // Gun drops slowly from upper-middle-left, aimed almost directly at nearby target.
                playerGun.x = arenaLeft + arenaW * 0.36f
                playerGun.y = arenaTop + arenaH * 0.20f // Drops from top
                playerGun.vx = 0f
                playerGun.vy = 20f // Slow downward drop
                playerGun.angle = -0.05f // Pointing right towards nearby target
                playerGun.angularVelocity = 0f
                playerGun.isDestroyed = false

                // Target is close to the gun (horizontal distance only ~24% of arena width)
                val targetX = arenaLeft + arenaW * 0.60f
                val targetY = arenaTop + arenaH * 0.22f
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                // Large generous platform shelf supporting the grenade target
                val platW = arenaW * 0.36f
                val platH = 18f
                val platX = targetX - platW * 0.5f
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))
                secondPlatformObstacle = null
            }
            2 -> {
                // Level 2: Small-to-medium distance.
                // Gun drops from top left, target placed at mid-right.
                playerGun.x = arenaLeft + arenaW * 0.30f
                playerGun.y = arenaTop + arenaH * 0.20f // Drops from top
                playerGun.vx = 0f
                playerGun.vy = 25f
                playerGun.angle = -0.10f
                playerGun.angularVelocity = 0f
                playerGun.isDestroyed = false

                // Target distance is ~38% of arena width
                val targetX = arenaLeft + arenaW * 0.68f
                val targetY = arenaTop + arenaH * 0.28f
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = arenaW * 0.32f
                val platH = 18f
                val platX = targetX - platW * 0.5f
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))
                secondPlatformObstacle = null
            }
            3 -> {
                // Level 3: Medium distance.
                playerGun.x = arenaLeft + arenaW * 0.25f
                playerGun.y = arenaTop + arenaH * 0.18f // Drops from top
                playerGun.vx = 0f
                playerGun.vy = 25f
                playerGun.angle = -0.15f
                playerGun.angularVelocity = 0f
                playerGun.isDestroyed = false

                // Target distance is ~50% of arena width
                val targetX = arenaLeft + arenaW * 0.75f
                val targetY = arenaTop + arenaH * 0.36f
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = arenaW * 0.30f
                val platH = 18f
                val platX = targetX - platW * 0.5f
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))
                secondPlatformObstacle = null
            }
            4 -> {
                // Level 4: Medium-long distance with slightly lower target shelf.
                playerGun.x = arenaLeft + arenaW * 0.22f
                playerGun.y = arenaTop + arenaH * 0.18f // Drops from top
                playerGun.vx = 0f
                playerGun.vy = 25f
                playerGun.angle = -0.20f
                playerGun.angularVelocity = 0f
                playerGun.isDestroyed = false

                // Target distance is ~58% of arena width, slightly lower
                val targetX = arenaLeft + arenaW * 0.80f
                val targetY = arenaTop + arenaH * 0.45f
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = arenaW * 0.28f
                val platH = 18f
                val platX = targetX - platW * 0.5f
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))
                secondPlatformObstacle = null
            }
            5 -> {
                // Level 5: Full distance across arena.
                playerGun.x = arenaLeft + arenaW * 0.18f
                playerGun.y = arenaTop + arenaH * 0.16f // Drops from top
                playerGun.vx = 0f
                playerGun.vy = 25f
                playerGun.angle = -0.22f
                playerGun.angularVelocity = 0f
                playerGun.isDestroyed = false

                // Target distance is ~65% of arena width
                val targetX = arenaLeft + arenaW * 0.83f
                val targetY = arenaTop + arenaH * 0.52f
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = arenaW * 0.26f
                val platH = 18f
                val platX = targetX - platW * 0.5f
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))
                secondPlatformObstacle = null
            }
            else -> {
                // Level 6+: Procedural / Randomized layout with varied positions & obstacles
                val rand = Random(levelNumber.toLong() * 31L + 7L)
                val spawnLeft = rand.nextBoolean()
                val gunSideX = if (spawnLeft) rand.nextFloat() * 0.15f + 0.16f else rand.nextFloat() * 0.15f + 0.68f
                val targetSideX = if (spawnLeft) rand.nextFloat() * 0.20f + 0.65f else rand.nextFloat() * 0.20f + 0.15f

                playerGun.x = arenaLeft + arenaW * gunSideX
                playerGun.y = arenaTop + arenaH * (rand.nextFloat() * 0.12f + 0.14f) // Drops from top
                playerGun.vx = 0f
                playerGun.vy = 25f
                playerGun.angle = if (spawnLeft) -0.15f else (-PI + 0.15f).toFloat()
                playerGun.angularVelocity = 0f
                playerGun.isDestroyed = false

                val targetX = arenaLeft + arenaW * targetSideX
                val targetY = arenaTop + arenaH * (rand.nextFloat() * 0.40f + 0.25f)
                target.x = targetX
                target.y = targetY
                target.vx = 0f
                target.vy = 0f
                target.isDestroyed = false

                val platW = arenaW * (rand.nextFloat() * 0.10f + 0.24f)
                val platH = 18f
                val platX = targetX - platW * 0.5f
                val platY = targetY + target.radius + 2f
                platformObstacle = PlatformObstacle(platX, platY, platW, platH, Color(0xFF475569))

                // Occasional bonus platform or barrier for high levels (level 7+)
                if (levelNumber >= 7 && rand.nextBoolean()) {
                    val midW = arenaW * 0.24f
                    val midH = 18f
                    val midX = arenaLeft + arenaW * 0.38f
                    val midY = arenaTop + arenaH * 0.42f
                    secondPlatformObstacle = PlatformObstacle(midX, midY, midW, midH, Color(0xFF334155))
                } else {
                    secondPlatformObstacle = null
                }
            }
        }
    }

    /**
     * Primary Control Action: Tap to shoot.
     * Fires bullet forward, applies equal & opposite recoil impulse and angular torque to gun.
     */
    fun tapToShoot(): Boolean {
        if (gameStatus != GameStatus.PLAYING || playerGun.isDestroyed) {
            return false
        }

        shotsFiredCount++
        playerGun.shotsFired++

        val barrelTip = playerGun.getBarrelTipOffset()
        val forward = playerGun.getForwardVector()
        val backward = playerGun.getBackwardVector()

        // 1. Spawn Ricochet Bullet at barrel tip with high forward velocity
        val bullet = RicochetBullet(
            id = nextBulletId++,
            x = barrelTip.x,
            y = barrelTip.y,
            vx = forward.x * bulletSpeed,
            vy = forward.y * bulletSpeed,
            radius = 6.5f,
            bouncesLeft = 4,
            maxBounces = 4
        )
        bullet.trailPoints.add(Offset(bullet.x, bullet.y))
        bullets.add(bullet)

        // 2. Apply Newtonian Recoil Impulse to the Gun
        playerGun.vx += backward.x * gunRecoilForce
        playerGun.vy += backward.y * gunRecoilForce

        // 3. Apply Rotational Torque (Angular Impulse)
        // Recoil torque causes the gun to tumble/spin gently and predictably
        playerGun.angularVelocity += gunRecoilTorque

        // 4. Spawn Muzzle Flash & Smoke Particles
        spawnMuzzleEffect(barrelTip, forward)

        return true
    }

    /**
     * Main Physics Simulation Update Loop (Fixed/Continuous timestep).
     */
    fun update(dt: Float) {
        if (!isInitialized) return
        val clampedDt = dt.coerceIn(0.001f, 0.033f)

        // 1. Update Gun Physics
        if (!playerGun.isDestroyed) {
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
        // Apply Gravity (Slow & Floaty)
        playerGun.vy += gravity * dt

        // Apply Linear & Angular Drag
        playerGun.vx *= (1f - linearDrag * dt).coerceIn(0f, 1f)
        playerGun.vy *= (1f - (linearDrag * 0.4f) * dt).coerceIn(0f, 1f)
        playerGun.angularVelocity *= (1f - angularDrag * dt).coerceIn(0f, 1f)

        // Integrate Position & Orientation
        playerGun.x += playerGun.vx * dt
        playerGun.y += playerGun.vy * dt
        playerGun.angle += playerGun.angularVelocity * dt

        // Normalize Angle between -PI and PI
        while (playerGun.angle > PI) playerGun.angle -= (2 * PI).toFloat()
        while (playerGun.angle < -PI) playerGun.angle += (2 * PI).toFloat()

        // Gun Collision against Arena Boundaries
        val halfW = playerGun.width * 0.45f
        val halfH = playerGun.height * 0.45f

        // Left Wall
        if (playerGun.x - halfW < arenaLeft) {
            playerGun.x = arenaLeft + halfW
            playerGun.vx = -playerGun.vx * wallRestitution
            playerGun.angularVelocity *= 0.85f
            spawnWallImpactSparks(arenaLeft, playerGun.y, 1f, 0f)
        }
        // Right Wall
        if (playerGun.x + halfW > arenaRight) {
            playerGun.x = arenaRight - halfW
            playerGun.vx = -playerGun.vx * wallRestitution
            playerGun.angularVelocity *= 0.85f
            spawnWallImpactSparks(arenaRight, playerGun.y, -1f, 0f)
        }
        // Ceiling
        if (playerGun.y - halfH < arenaTop) {
            playerGun.y = arenaTop + halfH
            playerGun.vy = -playerGun.vy * wallRestitution
            playerGun.angularVelocity *= 0.85f
            spawnWallImpactSparks(playerGun.x, arenaTop, 0f, 1f)
        }
        // Floor
        if (playerGun.y + halfH > arenaBottom) {
            playerGun.y = arenaBottom - halfH
            playerGun.vy = -playerGun.vy * wallRestitution
            // Ground friction reduces horizontal speed and spin
            playerGun.vx *= 0.90f
            playerGun.angularVelocity *= 0.70f
            if (abs(playerGun.vy) < 25f) {
                playerGun.vy = 0f
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

    private fun spawnMuzzleEffect(origin: Offset, forward: Offset) {
        val count = 12
        for (i in 0 until count) {
            val spreadAngle = (Random.nextFloat() - 0.5f) * 0.9f
            val speed = Random.nextFloat() * 220f + 80f
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
                    color = if (i % 2 == 0) Color(0xFFFFD54F) else Color(0xFFB0BEC5),
                    radius = Random.nextFloat() * 5f + 3f,
                    life = 0.32f,
                    maxLife = 0.32f
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
                    radius = Random.nextFloat() * 4.5f + 2f,
                    life = 0.38f,
                    maxLife = 0.38f
                )
            )
        }
    }

    private fun spawnTargetDestructionEffect(x: Float, y: Float) {
        // Spawn Debris Fragments
        val debrisCount = 16
        for (i in 0 until debrisCount) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = Random.nextFloat() * 400f + 140f
            debrisList.add(
                DebrisFragment(
                    x = x + (Random.nextFloat() - 0.5f) * 20f,
                    y = y + (Random.nextFloat() - 0.5f) * 20f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 140f,
                    angle = Random.nextFloat() * 6.28f,
                    angularVelocity = (Random.nextFloat() - 0.5f) * 16f,
                    width = Random.nextFloat() * 18f + 10f,
                    height = Random.nextFloat() * 16f + 8f,
                    color = if (i % 2 == 0) Color(0xFFFFD600) else Color(0xFFFF9100),
                    life = 1.6f,
                    maxLife = 1.6f
                )
            )
        }

        // Spawn Spark/Flame Particles
        val particleCount = 36
        for (i in 0 until particleCount) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val speed = Random.nextFloat() * 460f + 60f
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
                    radius = Random.nextFloat() * 6f + 2.5f,
                    life = Random.nextFloat() * 0.5f + 0.35f,
                    maxLife = 0.7f
                )
            )
        }
    }
}
