package dev.flipperdroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FlipperOrange = Color(0xFFFF8200)
val FlipperBg = Color(0xFF0B0B0B)
val FlipperPanel = Color(0xFF161616)
val FlipperLine = Color(0xFF2A2A2A)
val FlipperText = Color(0xFFE8E8E8)
val FlipperDim = Color(0xFF7D7D7D)
val FlipperGreen = Color(0xFF3DDC84)
val FlipperRed = Color(0xFFFF5252)

private val Scheme = darkColorScheme(
    primary = FlipperOrange,
    onPrimary = Color.Black,
    primaryContainer = FlipperPanel,
    onPrimaryContainer = FlipperOrange,
    background = FlipperBg,
    onBackground = FlipperText,
    surface = FlipperPanel,
    onSurface = FlipperText,
    surfaceVariant = FlipperPanel,
    onSurfaceVariant = FlipperDim,
    outline = FlipperLine,
    error = FlipperRed,
)

@Composable
fun FlipperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
