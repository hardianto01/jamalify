package com.jamalsquad.jamalify.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jamalsquad.jamalify.desktop.core.DesktopTheme

/*
 * Salinan palet ui/theme/Theme.kt. Berkas aslinya tidak bisa dibagi karena
 * JamalifyTheme membaca ThemeManager yang memakai SharedPreferences; kalau
 * warna di Android berubah, samakan juga di sini.
 */
private val Snow = Color(0xFFF4F5F9)
private val Night = Color(0xFF171C26)
private val NightElevated = Color(0xFF1F2633)
private val NightCard = Color(0xFF272E3D)

private val DarkColors = darkColorScheme(
    primary = Snow, onPrimary = Night,
    primaryContainer = Color(0xFF2E3342), onPrimaryContainer = Snow, inversePrimary = Night,
    secondary = Snow, onSecondary = Night,
    secondaryContainer = Color(0xFF2E3342), onSecondaryContainer = Snow,
    tertiary = Color(0xFFB9C0D4), onTertiary = Night,
    tertiaryContainer = Color(0xFF363C4D), onTertiaryContainer = Snow,
    background = Night, onBackground = Snow,
    surface = NightElevated, onSurface = Snow,
    surfaceVariant = NightCard, onSurfaceVariant = Color(0xFF7F869A), surfaceTint = Snow,
    surfaceDim = Color(0xFF101217), surfaceBright = Color(0xFF2C3140),
    surfaceContainerLowest = Color(0xFF0E1015), surfaceContainerLow = Color(0xFF191B23),
    surfaceContainer = NightElevated, surfaceContainerHigh = NightCard,
    surfaceContainerHighest = Color(0xFF2C3140),
    inverseSurface = Snow, inverseOnSurface = Night,
    error = Color(0xFFFF6B6B), onError = Color(0xFF370000),
    errorContainer = Color(0xFF5C1A1A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF363B4A), outlineVariant = Color(0xFF262A35), scrim = Color(0xFF000000)
)

private val LightColors = lightColorScheme(
    primary = Night, onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E8EF), onPrimaryContainer = Night, inversePrimary = Snow,
    secondary = Night, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E8EF), onSecondaryContainer = Night,
    tertiary = Color(0xFF4A5165), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE0EA), onTertiaryContainer = Night,
    background = Color.White, onBackground = Night,
    surface = Color.White, onSurface = Night,
    surfaceVariant = Color(0xFFF1F2F6), onSurfaceVariant = Color(0xFF7A8194), surfaceTint = Color.Transparent,
    surfaceDim = Color(0xFFE3E5EC), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF3F4F7), surfaceContainerHigh = Color(0xFFF1F2F6),
    surfaceContainerHighest = Color(0xFFE8EAF0),
    inverseSurface = Night, inverseOnSurface = Snow,
    error = Color(0xFFD93A3A), onError = Color.White,
    errorContainer = Color(0xFFFCE3E1), onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFFCDD1DC), outlineVariant = Color(0xFFE6E8EE), scrim = Color(0xFF000000)
)

private val JamalifyTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
)

val DesktopTheme.label: String
    get() = when (this) {
        DesktopTheme.SYSTEM -> "Ikuti sistem"
        DesktopTheme.LIGHT -> "Terang"
        DesktopTheme.DARK -> "Gelap"
    }

@Composable
fun JamalifyDesktopTheme(theme: DesktopTheme, content: @Composable () -> Unit) {
    val dark = when (theme) {
        DesktopTheme.SYSTEM -> isSystemInDarkTheme()
        DesktopTheme.LIGHT -> false
        DesktopTheme.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = JamalifyTypography,
        content = content
    )
}
