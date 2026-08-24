package com.jamalsquad.jamalify.ui.theme

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

val Mint = Color(0xFF00E0A4)
val MintDim = Color(0xFF00795A)
val Ink = Color(0xFF0A0E12)
val InkElevated = Color(0xFF141A21)
val InkCard = Color(0xFF1B232C)
val TextPrimary = Color(0xFFF2F5F7)
val TextMuted = Color(0xFF97A3B0)

/**
 * Seluruh peran warna Material 3 diisi eksplisit — termasuk yang jarang
 * disebut seperti `secondaryContainer` dan `surfaceContainerHigh`.
 *
 * Peran yang dibiarkan kosong tidak ikut gelap mengikuti sisa palet: Compose
 * mengisinya dengan baseline Material yang bernuansa ungu, sehingga pil tab
 * terpilih di bottom navigation dan latar AlertDialog muncul ungu di tengah
 * tema mint/hitam ini.
 */
private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF00281C),
    primaryContainer = Color(0xFF00553C),
    onPrimaryContainer = Color(0xFF7CFFD9),
    inversePrimary = MintDim,

    secondary = Mint,
    onSecondary = Color(0xFF00281C),
    secondaryContainer = Color(0xFF00553C),
    onSecondaryContainer = Color(0xFF7CFFD9),

    tertiary = Color(0xFF7FD1FF),
    onTertiary = Color(0xFF00344A),
    tertiaryContainer = Color(0xFF004C69),
    onTertiaryContainer = Color(0xFFC4EBFF),

    background = Ink,
    onBackground = TextPrimary,

    surface = InkElevated,
    onSurface = TextPrimary,
    surfaceVariant = InkCard,
    onSurfaceVariant = TextMuted,
    surfaceTint = Mint,

    surfaceDim = Color(0xFF080B0E),
    surfaceBright = Color(0xFF262F3A),
    surfaceContainerLowest = Color(0xFF05080A),
    surfaceContainerLow = Color(0xFF10161C),
    surfaceContainer = InkElevated,
    surfaceContainerHigh = InkCard,
    surfaceContainerHighest = Color(0xFF232D38),

    inverseSurface = Color(0xFFE6ECF1),
    inverseOnSurface = Color(0xFF10161C),

    error = Color(0xFFFF6B6B),
    onError = Color(0xFF370000),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF33404D),
    outlineVariant = Color(0xFF222D38),
    scrim = Color(0xFF000000)
)

private val LightColors = lightColorScheme(
    primary = MintDim,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9DF7DE),
    onPrimaryContainer = Color(0xFF002019),
    inversePrimary = Mint,

    secondary = MintDim,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF9DF7DE),
    onSecondaryContainer = Color(0xFF002019),

    tertiary = Color(0xFF00658C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4EBFF),
    onTertiaryContainer = Color(0xFF001E2C),

    background = Color(0xFFF7F9FA),
    onBackground = Color(0xFF10161C),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10161C),
    surfaceVariant = Color(0xFFE7ECF0),
    onSurfaceVariant = Color(0xFF56646F),
    surfaceTint = MintDim,

    surfaceDim = Color(0xFFD9DFE4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5F7),
    surfaceContainer = Color(0xFFECF0F3),
    surfaceContainerHigh = Color(0xFFE6EBEF),
    surfaceContainerHighest = Color(0xFFDFE6EA),

    inverseSurface = Color(0xFF2E3742),
    inverseOnSurface = Color(0xFFF0F4F7),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    outline = Color(0xFF6F7C88),
    outlineVariant = Color(0xFFC3CDD6),
    scrim = Color(0xFF000000)
)

private val JamalifyTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
)

/**
 * Aplikasi ini selalu gelap, seperti kebanyakan pemutar musik.
 *
 * Sebelumnya tema mengikuti setelan sistem lewat [isSystemInDarkTheme]. Itu
 * membuka satu kelas bug yang sulit dilacak: sebagian OEM punya "dark mode"
 * yang tidak menyetel `uiMode=night`, lalu membalik warna aplikasi sendiri —
 * hasilnya teks gelap di atas latar gelap. Dengan mengunci ke satu palet dan
 * mematikan `forceDarkAllowed`, tidak ada lagi jalur di mana warna bisa
 * ditentukan pihak lain.
 *
 * Palet terang di bawah tetap disimpan; untuk menghidupkan lagi, kembalikan
 * nilai bawaan [darkTheme] menjadi `isSystemInDarkTheme()`.
 */
@Composable
fun JamalifyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = JamalifyTypography,
        content = content
    )
}
