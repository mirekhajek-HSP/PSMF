package cz.hspinovace.psmf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.noto_sans_bold
import cz.hspinovace.psmf.resources.noto_sans_medium
import cz.hspinovace.psmf.resources.noto_sans_regular
import cz.hspinovace.psmf.resources.oswald_bold
import cz.hspinovace.psmf.resources.oswald_regular
import org.jetbrains.compose.resources.Font

// The neutrals. Chosen for contrast in direct sun rather than for fashion:
// mid-tone greys and pastel surfaces are the first things to disappear on a
// bright pitch, and the referee reading this is outdoors.
private val InkSoft = Color(0xFF4A4A4A)
private val LineLight = Color(0xFFD9D9D9)
private val FieldLight = Color(0xFFE6E6E6)
private val PageDark = Color(0xFF121212)
private val SurfaceDark = Color(0xFF1C1C1C)
private val FieldDark = Color(0xFF2E2E2E)
private val PaperDark = Color(0xFFF2F2F2)
private val InkSoftDark = Color(0xFFC9C9C9)
private val LineDark = Color(0xFF3A3A3A)

// The red card is the same red in both themes, because it is a fill and it
// has to look like the card. `error`, which Material also uses for warning
// *text*, is the one thing lightened for the dark theme: #D60010 on #1C1C1C
// is 2.4:1 and unreadable, and a warning nobody can read is worse than no
// warning at all -- this app's whole discipline around eligibility is that
// it warns and never clears.
private val AlertOnDark = Color(0xFFFF8A82)

private val LightScheme =
    lightColorScheme(
        primary = PsmfBrand.Ink,
        onPrimary = PsmfBrand.Surface,
        primaryContainer = PsmfBrand.Yellow,
        onPrimaryContainer = PsmfBrand.Ink,
        secondary = InkSoft,
        onSecondary = PsmfBrand.Surface,
        secondaryContainer = PsmfBrand.Yellow,
        onSecondaryContainer = PsmfBrand.Ink,
        tertiary = PsmfBrand.Ink,
        onTertiary = PsmfBrand.Surface,
        tertiaryContainer = Color(0xFFFFE9B0),
        onTertiaryContainer = PsmfBrand.Ink,
        background = PsmfBrand.Page,
        onBackground = PsmfBrand.Ink,
        surface = PsmfBrand.Surface,
        onSurface = PsmfBrand.Ink,
        surfaceVariant = FieldLight,
        onSurfaceVariant = InkSoft,
        outline = Color(0xFF767676),
        outlineVariant = LineLight,
        error = PsmfBrand.Alert,
        onError = PsmfBrand.Surface,
        errorContainer = PsmfBrand.Alert,
        onErrorContainer = PsmfBrand.Surface,
    )

private val DarkScheme =
    darkColorScheme(
        primary = PaperDark,
        onPrimary = PsmfBrand.Ink,
        primaryContainer = PsmfBrand.Yellow,
        onPrimaryContainer = PsmfBrand.Ink,
        secondary = InkSoftDark,
        onSecondary = PsmfBrand.Ink,
        secondaryContainer = PsmfBrand.Yellow,
        onSecondaryContainer = PsmfBrand.Ink,
        tertiary = PaperDark,
        onTertiary = PsmfBrand.Ink,
        tertiaryContainer = Color(0xFF5A4300),
        onTertiaryContainer = Color(0xFFFFE9B0),
        background = PageDark,
        onBackground = PaperDark,
        surface = SurfaceDark,
        onSurface = PaperDark,
        surfaceVariant = FieldDark,
        onSurfaceVariant = InkSoftDark,
        outline = Color(0xFF8F8F8F),
        outlineVariant = LineDark,
        error = AlertOnDark,
        onError = Color(0xFF4A0004),
        errorContainer = PsmfBrand.Alert,
        onErrorContainer = PsmfBrand.Surface,
    )

@Composable
fun PsmfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = psmfTypography(),
        content = content,
    )
}

/**
 * The type scale is golblok's — the sizes, weights, line heights and letter
 * spacing there are sound and were worth taking. **The font families are
 * not**: its `Type.kt` carries a comment admitting the real files were
 * never added and both families resolve to `FontFamily.SansSerif`.
 *
 * Oswald for display, because it is the nearest condensed grotesque to
 * PSMF's own Anton *and has Cyrillic*, which Anton does not. Noto Sans for
 * body, for the same reason and because it is what golblok always meant to
 * load. Bundled, never fetched: the app has no network by design and a
 * referee on a pitch may have no signal.
 *
 * Nothing here is in `dp`. Every size is `sp`, so the system font scale
 * applies — the referee population skews older and many will have it turned
 * up.
 */
@Composable
private fun psmfTypography(): Typography {
    val display =
        FontFamily(
            Font(Res.font.oswald_regular, FontWeight.Normal),
            Font(Res.font.oswald_bold, FontWeight.Bold),
        )
    val body =
        FontFamily(
            Font(Res.font.noto_sans_regular, FontWeight.Normal),
            Font(Res.font.noto_sans_medium, FontWeight.Medium),
            Font(Res.font.noto_sans_bold, FontWeight.Bold),
        )
    return remember(display, body) { Typography().withFamilies(display, body) }
}

private fun Typography.withFamilies(
    display: FontFamily,
    body: FontFamily,
): Typography =
    Typography(
        displayLarge = displayLarge.copy(fontFamily = display),
        displayMedium = displayMedium.copy(fontFamily = display),
        displaySmall = displaySmall.copy(fontFamily = display),
        headlineLarge = headlineLarge.copy(fontFamily = display),
        headlineMedium =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.5).sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge = titleLarge.copy(fontFamily = display),
        titleMedium =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.1.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = display,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall = bodySmall.copy(fontFamily = body),
        labelLarge =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium = labelMedium.copy(fontFamily = body),
        labelSmall =
            TextStyle(
                fontFamily = body,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )
