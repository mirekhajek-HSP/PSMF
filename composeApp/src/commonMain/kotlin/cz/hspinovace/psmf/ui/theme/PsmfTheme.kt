package cz.hspinovace.psmf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deep blue against near-white, and near-white against near-black. Chosen
// for contrast in direct sun rather than for fashion: mid-tone greys and
// pastel surfaces are the first things to disappear on a bright pitch.
private val Ink = Color(0xFF10151C)
private val Paper = Color(0xFFFCFCFD)
private val Blue = Color(0xFF004E9A)
private val BlueLight = Color(0xFF9CC7FF)
private val Amber = Color(0xFF7A4E00)
private val AmberLight = Color(0xFFFFD08A)
private val Danger = Color(0xFF8C0F14)
private val DangerLight = Color(0xFFFFB3B0)

private val LightScheme =
    lightColorScheme(
        primary = Blue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E5FF),
        onPrimaryContainer = Color(0xFF001B3C),
        secondary = Amber,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE3B8),
        onSecondaryContainer = Color(0xFF2A1700),
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = Color(0xFFE6E9EF),
        onSurfaceVariant = Color(0xFF2C3138),
        outline = Color(0xFF5A616B),
        error = Danger,
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD7),
        onErrorContainer = Color(0xFF41000B),
    )

private val DarkScheme =
    darkColorScheme(
        primary = BlueLight,
        onPrimary = Color(0xFF00305F),
        primaryContainer = Color(0xFF004583),
        onPrimaryContainer = Color(0xFFD6E5FF),
        secondary = AmberLight,
        onSecondary = Color(0xFF412D00),
        secondaryContainer = Color(0xFF5D4200),
        onSecondaryContainer = Color(0xFFFFE3B8),
        background = Color(0xFF0D1116),
        onBackground = Color(0xFFE6E9EF),
        surface = Color(0xFF0D1116),
        onSurface = Color(0xFFE6E9EF),
        surfaceVariant = Color(0xFF3A4048),
        onSurfaceVariant = Color(0xFFC7CCD4),
        outline = Color(0xFF8F959E),
        error = DangerLight,
        onError = Color(0xFF5B0009),
        errorContainer = Color(0xFF7A0D12),
        onErrorContainer = Color(0xFFFFDAD7),
    )

@Composable
fun PsmfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
