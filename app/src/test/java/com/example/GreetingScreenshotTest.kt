package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.screens.ArenaGameScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.game.engine.GameEngine
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickshot_arena_screenshot() {
        val engine = GameEngine(levelNumber = 1)
        engine.initializeArena(1000f, 1600f)

        composeTestRule.setContent {
            MyApplicationTheme {
                ArenaGameScreen(
                    engine = engine,
                    gameTick = 1L,
                    onInitArena = { _, _ -> },
                    onTapScreen = {},
                    onRestartLevel = {},
                    onNextLevel = {},
                    onExitArena = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
