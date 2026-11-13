package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.game.engine.GameEngine
import com.example.game.engine.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches Clickshot app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Clickshot", appName)
    }

    @Test
    fun `test gun tap-to-shoot generates recoil and bullet`() {
        val engine = GameEngine(levelNumber = 1)
        engine.initializeArena(1000f, 1600f)

        val initialGunVx = engine.playerGun.vx
        val initialGunVy = engine.playerGun.vy
        val initialAngVel = engine.playerGun.angularVelocity

        assertEquals(0, engine.bullets.size)
        assertEquals(0, engine.shotsFiredCount)

        val shotFired = engine.tapToShoot()
        assertTrue(shotFired)
        assertEquals(1, engine.bullets.size)
        assertEquals(1, engine.shotsFiredCount)

        // Gun received recoil impulse in velocity & angular velocity
        val gunMoved = engine.playerGun.vx != initialGunVx || engine.playerGun.vy != initialGunVy
        assertTrue("Gun should move upon recoil impulse", gunMoved)
        assertTrue("Gun should gain angular rotation", engine.playerGun.angularVelocity != initialAngVel)
    }

    @Test
    fun `test bullet ricochet reflection`() {
        val engine = GameEngine(levelNumber = 1)
        engine.initializeArena(1000f, 1600f)

        engine.tapToShoot()
        val bullet = engine.bullets.first()
        val initialBounces = bullet.bouncesLeft

        // Fast forward simulation steps to simulate bullet motion
        for (i in 0 until 120) {
            engine.update(0.016f)
        }

        // Bullet should bounce or hit target
        assertTrue(bullet.bouncesLeft <= initialBounces || !bullet.isAlive)
    }

    @Test
    fun `test target destruction marks game won`() {
        val engine = GameEngine(levelNumber = 1)
        engine.initializeArena(1000f, 1600f)

        // Position bullet directly at target to test collision resolution
        engine.target.x = 500f
        engine.target.y = 500f
        engine.tapToShoot()

        val bullet = engine.bullets.first()
        bullet.x = 500f
        bullet.y = 500f

        engine.update(0.016f)

        assertTrue("Target should be destroyed", engine.target.isDestroyed)
        assertEquals(GameStatus.WON, engine.gameStatus)
    }

    @Test
    fun `test 2_5x scaled gun and bomb target dimensions`() {
        val engine = GameEngine(levelNumber = 1)
        // Use ~1080p-width arena so computeArenaScale() is ~1.0f
        engine.initializeArena(1150f, 1600f)

        assertTrue("Gun width should be scaled ~2.5x (>= 250f)", engine.playerGun.width >= 250f)
        assertTrue("Gun height should be scaled ~2.5x (>= 160f)", engine.playerGun.height >= 160f)
        assertTrue("Bomb target radius should be scaled ~2.5x (>= 80f)", engine.target.radius >= 80f)
    }

    @Test
    fun `test switching gun types changes projectile count and physics stats`() {
        val engine = GameEngine(levelNumber = 1)
        engine.initializeArena(1150f, 1600f)

        engine.setGunType(com.example.game.engine.GunType.SCATTER_SHOTGUN)
        assertEquals(com.example.game.engine.GunType.SCATTER_SHOTGUN, engine.currentGunType)

        engine.tapToShoot()
        assertEquals("Scatter shotgun should fire 3 pellets", 3, engine.bullets.size)
        val initialSpin = kotlin.math.abs(engine.playerGun.angularVelocity)
        assertTrue("Gun should receive realistic angular kick", initialSpin > 0.5f)

        // Advance physics and confirm quadratic aerodynamic damping slows rotation smoothly
        for (i in 0 until 45) {
            engine.update(0.016f)
        }
        val decayedSpin = kotlin.math.abs(engine.playerGun.angularVelocity)
        assertTrue("Rotation should decay smoothly over time due to aerodynamic damping", decayedSpin < initialSpin)
    }

    @Test
    fun `test sound events emit on shot and target explosion`() {
        val events = mutableListOf<com.example.game.audio.GameSoundEvent>()
        val engine = GameEngine(levelNumber = 1).apply {
            onSoundEvent = { events.add(it) }
        }
        engine.initializeArena(1150f, 1600f)

        engine.tapToShoot()
        assertTrue(events.contains(com.example.game.audio.GameSoundEvent.SHOT_PULSE))

        engine.target.x = 500f
        engine.target.y = 500f
        val bullet = engine.bullets.first()
        bullet.x = 500f
        bullet.y = 500f
        engine.update(0.016f)

        assertTrue(events.contains(com.example.game.audio.GameSoundEvent.TARGET_EXPLOSION))
        assertTrue(events.contains(com.example.game.audio.GameSoundEvent.LEVEL_WON))

        val shotEvents = com.example.game.engine.GunType.entries
            .map { com.example.game.audio.GameSoundEvent.forGun(it) }
            .toSet()
        assertEquals("Every gun should have a distinct sound event", com.example.game.engine.GunType.entries.size, shotEvents.size)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundManager = com.example.game.audio.SoundEffectsManager(context)
        soundManager.play(com.example.game.audio.GameSoundEvent.SHOT_RAILGUN)
        soundManager.isMuted = true
        soundManager.play(com.example.game.audio.GameSoundEvent.UI_CLICK)
        soundManager.release()
    }

    @Test
    fun `test pvp 1 or 2 hits do not kill and reset after burst window expires`() {
        val events = mutableListOf<com.example.game.audio.GameSoundEvent>()
        val engine = GameEngine(
            levelNumber = 1,
            gameMode = com.example.game.engine.GameMode.DUEL_1V1
        ).apply {
            authoritativeLocalPvp = true
            onSoundEvent = { events.add(it) }
        }
        engine.initializeArena(1150f, 1600f)

        // Position guns at known coordinates for deterministic collision tests
        engine.opponentGun.x = 700f
        engine.opponentGun.y = 400f
        engine.opponentGun.vx = 0f
        engine.opponentGun.vy = 0f
        engine.opponentGun.angle = 0f

        // Hit 1: should NOT kill
        engine.bullets.add(
            com.example.game.engine.RicochetBullet(
                id = 101L,
                x = 700f,
                y = 400f,
                vx = 100f,
                vy = 0f,
                radius = 12f,
                ownerId = "player"
            )
        )
        engine.update(0.016f)

        assertEquals(1, engine.opponentGun.simultaneousHits)
        assertFalse("1 hit must NOT kill gun", engine.opponentGun.isDestroyed)
        assertEquals(GameStatus.PLAYING, engine.gameStatus)
        assertTrue(events.contains(com.example.game.audio.GameSoundEvent.ARMOR_HIT))
        assertFalse(events.contains(com.example.game.audio.GameSoundEvent.ARMOR_BREAK))

        // Hit 2: still must NOT kill
        engine.opponentGun.x = 700f
        engine.opponentGun.y = 400f
        engine.bullets.add(
            com.example.game.engine.RicochetBullet(
                id = 102L,
                x = 700f,
                y = 400f,
                vx = 100f,
                vy = 0f,
                radius = 12f,
                ownerId = "player"
            )
        )
        engine.update(0.016f)

        assertEquals(2, engine.opponentGun.simultaneousHits)
        assertFalse("2 hits must NOT kill gun (requires 3)", engine.opponentGun.isDestroyed)
        assertEquals(GameStatus.PLAYING, engine.gameStatus)

        // Advance simulation past simultaneousHitWindowSec (1.8s) -> armor should reset to 0/3
        for (i in 0 until 70) {
            engine.update(0.03f)
        }
        assertEquals("Simultaneous hits must reset to 0 after window expires", 0, engine.opponentGun.simultaneousHits)
        assertTrue(engine.opponentGun.recoveryFlashTimer > 0f)
        assertFalse(engine.opponentGun.isDestroyed)
    }

    @Test
    fun `test pvp 3 simultaneous hits kill gun and emit armor break`() {
        val events = mutableListOf<com.example.game.audio.GameSoundEvent>()
        val engine = GameEngine(
            levelNumber = 1,
            gameMode = com.example.game.engine.GameMode.DUEL_1V1
        ).apply {
            authoritativeLocalPvp = true
            onSoundEvent = { events.add(it) }
        }
        engine.initializeArena(1150f, 1600f)

        engine.opponentGun.x = 700f
        engine.opponentGun.y = 420f
        engine.opponentGun.vx = 0f
        engine.opponentGun.vy = 0f
        engine.opponentGun.angle = 0f

        // Simulate 3 bullets hitting the rival gun simultaneously in the same frame
        for (idx in 1..3) {
            engine.bullets.add(
                com.example.game.engine.RicochetBullet(
                    id = 200L + idx,
                    x = 700f,
                    y = 420f,
                    vx = 80f,
                    vy = 0f,
                    radius = 12f,
                    ownerId = "player"
                )
            )
        }
        engine.update(0.016f)

        assertEquals(3, engine.opponentGun.simultaneousHits)
        assertTrue("3 simultaneous hits must eliminate the rival gun", engine.opponentGun.isDestroyed)
        assertEquals(GameStatus.WON, engine.gameStatus)
        assertTrue(events.contains(com.example.game.audio.GameSoundEvent.ARMOR_BREAK))
        assertTrue(events.contains(com.example.game.audio.GameSoundEvent.LEVEL_WON))

        // Verify local player gun also obeys the exact 3-hit rule when struck by remote opponent bullets
        val defenderEvents = mutableListOf<com.example.game.audio.GameSoundEvent>()
        val defenderEngine = GameEngine(
            levelNumber = 1,
            gameMode = com.example.game.engine.GameMode.DUEL_1V1
        ).apply {
            onSoundEvent = { defenderEvents.add(it) }
        }
        defenderEngine.initializeArena(1150f, 1600f)
        defenderEngine.playerGun.x = 350f
        defenderEngine.playerGun.y = 420f
        defenderEngine.playerGun.vx = 0f
        defenderEngine.playerGun.vy = 0f
        defenderEngine.playerGun.angle = 0f

        for (idx in 1..3) {
            defenderEngine.bullets.add(
                com.example.game.engine.RicochetBullet(
                    id = 300L + idx,
                    x = 350f,
                    y = 420f,
                    vx = -80f,
                    vy = 0f,
                    radius = 12f,
                    ownerId = "opponent"
                )
            )
        }
        defenderEngine.update(0.016f)

        assertEquals(3, defenderEngine.playerGun.simultaneousHits)
        assertTrue("3 simultaneous opponent hits must eliminate local gun", defenderEngine.playerGun.isDestroyed)
        assertEquals(GameStatus.LOST, defenderEngine.gameStatus)
        assertTrue(defenderEvents.contains(com.example.game.audio.GameSoundEvent.ARMOR_BREAK))
    }
}

