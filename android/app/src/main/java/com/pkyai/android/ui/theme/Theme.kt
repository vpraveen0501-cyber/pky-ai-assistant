package com.pkyai.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.pkyai.android.R

// Glassmorphism 2.0 Shapes
val GlassShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(24.dp), // More rounded
    large = RoundedCornerShape(32.dp)   // Ultra rounded like iOS/Glass 2.0
)

// Inter Font Family Setup
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val InterFont = GoogleFont("Inter")

val InterFontFamily = FontFamily(
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Black)
)

val PkyAiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        letterSpacing = (-1.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp
    )
)

private val DarkColorScheme = darkColorScheme(
    primary = PkyAiPrimary,
    secondary = PkyAiSecondary,
    tertiary = PkyAiTertiary,
    background = SurfaceDim,
    surface = SurfaceContainer,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = GlassStroke
)

private val MidnightGalaxyColorScheme = darkColorScheme(
    primary = GalaxyLavender,
    secondary = GalaxyCosmicBlue,
    tertiary = GalaxySilver,
    background = GalaxyDeepPurple,
    surface = GalaxyCosmicBlue.copy(alpha = 0.3f),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = GalaxySilver,
    onSurface = GalaxySilver
)

private val OceanDepthsColorScheme = darkColorScheme(
    primary = OceanTeal,
    secondary = OceanSeafoam,
    tertiary = OceanCream,
    background = OceanDeepNavy,
    surface = OceanTeal.copy(alpha = 0.2f),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = OceanCream,
    onSurface = OceanCream
)

@Composable
fun PkyAiTheme(
    darkTheme: Boolean = true,
    themeType: PkyAiThemeType = PkyAiThemeState.currentTheme,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeType) {
        PkyAiThemeType.MIDNIGHT_GALAXY -> MidnightGalaxyColorScheme
        PkyAiThemeType.OCEAN_DEPTHS -> OceanDepthsColorScheme
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PkyAiTypography,
        shapes = GlassShapes,
        content = content
    )
}
