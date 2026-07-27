package io.github.ukemeikot.flicksoccer

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.ukemeikot.flicksoccer.di.appModule
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import io.github.ukemeikot.flicksoccer.ui.game.GameScreen
import io.github.ukemeikot.flicksoccer.ui.menu.MenuScreen
import io.github.ukemeikot.flicksoccer.ui.settings.SettingsScreen
import io.github.ukemeikot.flicksoccer.ui.theme.FlickSoccerTheme
import org.koin.compose.KoinApplication

/** Simple sealed-class navigation (3 screens) — see §2 of the design brief. */
sealed interface Screen {
    data object Menu : Screen
    data class Game(val vsAi: Boolean, val difficulty: Difficulty) : Screen
    data object Settings : Screen
}

/**
 * App entry composable. Initializes Koin in-Compose and boots **straight to the menu** — there is
 * no login, account, or network gate anywhere in v1.
 */
@Composable
fun App(showHaptics: Boolean = true) {
    KoinApplication(application = { modules(appModule) }) {
        FlickSoccerTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                var screen: Screen by remember { mutableStateOf(Screen.Menu) }

                when (val s = screen) {
                    is Screen.Menu -> MenuScreen(
                        onPlayVsAi = { difficulty -> screen = Screen.Game(vsAi = true, difficulty = difficulty) },
                        onTwoPlayer = { screen = Screen.Game(vsAi = false, difficulty = Difficulty.MEDIUM) },
                        onSettings = { screen = Screen.Settings },
                    )

                    is Screen.Game -> GameScreen(
                        vsAi = s.vsAi,
                        difficulty = s.difficulty,
                        onExit = { screen = Screen.Menu },
                    )

                    is Screen.Settings -> SettingsScreen(
                        onBack = { screen = Screen.Menu },
                        showHaptics = showHaptics,
                    )
                }
            }
        }
    }
}
