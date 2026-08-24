package com.jamalsquad.jamalify.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jamalsquad.jamalify.R
import com.jamalsquad.jamalify.ui.components.VSpace

/**
 * Layar pembuka: logo penuh sebelum masuk aplikasi.
 *
 * Bukan dialog dan bukan modal — ini lapisan buram yang menutupi seluruh layar
 * lalu memudar, jadi aplikasi di belakangnya sudah sempat menyusun diri dan
 * tidak ada kedipan saat splash-nya hilang.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.82f,
        animationSpec = tween(durationMillis = 620),
        label = "splash_logo_scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "splash_logo_alpha"
    )
    val captionAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 320),
        label = "splash_caption_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = "Logo Jamalify",
                modifier = Modifier
                    .size(148.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    }
            )
            VSpace(20.dp)
            Text(
                text = "Jamalify",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.alpha(logoAlpha)
            )
        }

        Text(
            text = "by Hardianto ❤️",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .alpha(captionAlpha)
        )
    }
}
