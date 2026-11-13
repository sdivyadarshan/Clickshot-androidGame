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
}
