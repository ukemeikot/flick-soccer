package io.github.ukemeikot.flicksoccer

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Flick Soccer",
        state = rememberWindowState(size = DpSize(480.dp, 800.dp)),
    ) {
        // Desktop has no haptics — hide the setting.
        App(showHaptics = false)
    }
}
