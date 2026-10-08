package com.jamalsquad.jamalify.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Mint = Color(0xFF00E0A4)
val MintDim = Color(0xFF00795A)

// Palet monokrom: navy nyaris hitam dan putih, tanpa warna aksen.
val Snow = Color(0xFFF4F5F9)
val Night = Color(0xFF171C26)
val NightElevated = Color(0xFF1F2633)
val NightCard = Color(0xFF272E3D)
val TextPrimary = Color(0xFFF4F5F9)
val TextMuted = Color(0xFF7F869A)

/**
 * Seluruh peran warna Material 3 diisi eksplisit — termasuk yang jarang
 * disebut seperti `secondaryContainer` dan `surfaceContainerHigh`.
 *
 * Kedua tema monokrom: yang aktif atau terpilih ditandai warna teks utama,
 * sisanya abu-abu redup — tidak ada warna aksen.
 *
 * Peran yang dibiarkan kosong tidak ikut gelap mengikuti sisa palet: Compose
 * mengisinya dengan baseline Material yang bernuansa ungu, sehingga pil tab
 * terpilih di bottom navigation dan latar AlertDialog muncul ungu di tengah
 * tema gelap ini.
 */
private val DarkColors = darkColorScheme(
    primary = Snow,
    onPrimary = Night,
    primaryContainer = Color(0xFF2E3342),
    onPrimaryContainer = Snow,
    inversePrimary = Night,

    secondary = Snow,
    onSecondary = Night,
    secondaryContainer = Color(0xFF2E3342),
    onSecondaryContainer = Snow,

    tertiary = Color(0xFFB9C0D4),
    onTertiary = Night,
    tertiaryContainer = Color(0xFF363C4D),
    onTertiaryContainer = Snow,

    background = Night,
    onBackground = TextPrimary,

    surface = NightElevated,
    onSurface = TextPrimary,
    surfaceVariant = NightCard,
    onSurfaceVariant = TextMuted,
    surfaceTint = Snow,

    surfaceDim = Color(0xFF101217),
    surfaceBright = Color(0xFF2C3140),
    surfaceContainerLowest = Color(0xFF0E1015),
    surfaceContainerLow = Color(0xFF191B23),
    surfaceContainer = NightElevated,
    surfaceContainerHigh = NightCard,
    surfaceContainerHighest = Color(0xFF2C3140),

    inverseSurface = Snow,
    inverseOnSurface = Night,

    error = Color(0xFFFF6B6B),
    onError = Color(0xFF370000),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF363B4A),
    outlineVariant = Color(0xFF262A35),
    scrim = Color(0xFF000000)
)

private val LightColors = lightColorScheme(
    primary = Night,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E8EF),
    onPrimaryContainer = Night,
    inversePrimary = Snow,

    secondary = Night,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E8EF),
    onSecondaryContainer = Night,

    tertiary = Color(0xFF4A5165),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE0EA),
    onTertiaryContainer = Night,

    background = Color.White,
    onBackground = Night,

    surface = Color.White,
    onSurface = Night,
    surfaceVariant = Color(0xFFF1F2F6),
    onSurfaceVariant = Color(0xFF7A8194),
    surfaceTint = Color.Transparent,

    surfaceDim = Color(0xFFE3E5EC),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF3F4F7),
    surfaceContainerHigh = Color(0xFFF1F2F6),
    surfaceContainerHighest = Color(0xFFE8EAF0),

    inverseSurface = Night,
    inverseOnSurface = Snow,

    error = Color(0xFFD93A3A),
    onError = Color.White,
    errorContainer = Color(0xFFFCE3E1),
    onErrorContainer = Color(0xFF410E0B),

    outline = Color(0xFFCDD1DC),
    outlineVariant = Color(0xFFE6E8EE),
    scrim = Color(0xFF000000)
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

/**
 * Tema dipilih lewat [ThemeManager]: ikut sistem, terang, atau gelap.
 *
 * `forceDarkAllowed` dimatikan di tema jendela: sebagian OEM membalik warna
 * aplikasi sendiri di atas palet ini, hasilnya teks gelap di atas latar
 * gelap. Kalau mode gelap OEM tidak terdeteksi sebagai sistem, pengguna
 * bisa memilih Gelap secara manual.
 */
@Composable
fun JamalifyTheme(
    darkTheme: Boolean = isDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = JamalifyTypography,
        content = content
    )
}

@Composable
fun isDarkTheme(): Boolean {
    val mode by ThemeManager.mode.collectAsState()
    return when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
