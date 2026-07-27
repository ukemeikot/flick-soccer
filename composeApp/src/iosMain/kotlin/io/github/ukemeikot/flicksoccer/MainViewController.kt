package io.github.ukemeikot.flicksoccer

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point consumed by the SwiftUI/`UIViewControllerRepresentable` wrapper in `iosApp`. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    // Boots straight to the menu — no login/account gate.
    App(showHaptics = true)
}
