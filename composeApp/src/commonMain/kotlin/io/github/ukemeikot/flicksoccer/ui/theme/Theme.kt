package io.github.ukemeikot.flicksoccer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PitchGreen = Color(0xFF2A8C3D)
private val PitchGreenDark = Color(0xFF12341C)
private val TeamBlue = Color(0xFF3474F2)
private val TeamRed = Color(0xFFEA4747)

private val DarkColors = darkColorScheme(
    primary = TeamBlue,
    secondary = TeamRed,
    background = PitchGreenDark,
    surface = Color(0xFF17281C),
)

private val LightColors = lightColorScheme(
    primary = TeamBlue,
    secondary = TeamRed,
    background = PitchGreen,
    surface = Color(0xFFEAF3EC),
)

@Composable
fun FlickSoccerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
