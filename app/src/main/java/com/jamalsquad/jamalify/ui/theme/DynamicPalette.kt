package com.jamalsquad.jamalify.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.jamalsquad.jamalify.util.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AmbientColors(
    val topColor: Color = Color(0xFF0F1720),
    val centerColor: Color = Color(0xFF0A0E12),
    val accentColor: Color = Color(0xFF00E0A4)
)

@Composable
fun rememberAmbientColors(imageUrl: String?): AmbientColors {
    val context = LocalContext.current
    // Tanpa key: warna lagu sebelumnya bertahan sampai warna lagu baru siap,
    // jadi latar tidak berkedip ke warna bawaan setiap ganti lagu.
    val ambientState = remember {
        mutableStateOf(
            AmbientColors(
                topColor = Color(0xFF16202A),
                centerColor = Color(0xFF0A0E12),
                accentColor = Color(0xFF00E0A4)
            )
        )
    }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        val colors = extractAmbientColors(context, imageUrl)
        if (colors != null) {
            ambientState.value = colors
        }
    }

    return ambientState.value
}

private suspend fun extractAmbientColors(context: Context, imageUrl: String): AmbientColors? =
    withContext(Dispatchers.IO) {
        runCatching {
            // Palette cukup dengan gambar kecil; gambar penuh hanya
            // memperlambat dekode dan analisis warnanya.
            val request = ImageRequest.Builder(context)
                .data(Thumbnails.sized(imageUrl, 128))
                .size(128)
                .allowHardware(false)
                .build()

            val result = context.imageLoader.execute(request)
            if (result !is SuccessResult) return@runCatching null

            val drawable = result.drawable as? BitmapDrawable ?: return@runCatching null
            val bitmap = drawable.bitmap ?: return@runCatching null

            val palette = Palette.from(bitmap).generate()

            val vibrant = palette.getVibrantColor(0)
            val lightVibrant = palette.getLightVibrantColor(0)
            val darkVibrant = palette.getDarkVibrantColor(0)
            val dominant = palette.getDominantColor(0)
            val darkMuted = palette.getDarkMutedColor(0)

            val accentInt = when {
                vibrant != 0 -> vibrant
                lightVibrant != 0 -> lightVibrant
                dominant != 0 -> dominant
                else -> 0xFF1DB954.toInt()
            }

            val topBgInt = when {
                darkVibrant != 0 -> darkVibrant
                darkMuted != 0 -> darkMuted
                dominant != 0 -> dominant
                else -> 0xFF121212.toInt()
            }

            AmbientColors(
                topColor = Color(topBgInt).copy(alpha = 0.88f),
                centerColor = Color(0xFF121212),
                accentColor = Color(accentInt)
            )
        }.getOrNull()
    }
