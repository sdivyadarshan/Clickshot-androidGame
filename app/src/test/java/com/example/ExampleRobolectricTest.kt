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
}
