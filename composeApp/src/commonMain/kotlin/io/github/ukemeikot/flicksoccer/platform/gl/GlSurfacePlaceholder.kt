package io.github.ukemeikot.flicksoccer.platform.gl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Temporary stand-in for the native GL surface used by all three platform actuals in M0. Replaced
 * per platform by the real OpenGL host in M2 (desktop) and M3 (Android/iOS).
 */
@Composable
internal fun GlSurfacePlaceholder(modifier: Modifier, label: String) {
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF0E1A12)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color(0xFF9CC7A6),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(24.dp),
        )
    }
}
