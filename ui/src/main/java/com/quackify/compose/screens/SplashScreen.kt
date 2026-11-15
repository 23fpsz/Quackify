package com.quackify.compose.screens

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.theme.DmMono
import com.quackify.compose.theme.EncoreColors
import kotlin.math.exp

private const val BAND = 0.17f
private const val DIM = 0.16f
private const val SWEEP_MS = 2500
private const val BREATHE_MS = 3400

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember { buildLabel(context) }
    val wordmark = stringResource(R.string.quackify)
    val interaction = remember { MutableInteractionSource() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reveal by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = 460,
            delayMillis = 90,
            easing = LinearOutSlowInEasing,
        ),
        label = "splashReveal",
    )

    val sweep = rememberInfiniteTransition(label = "splashSweep")
    val head by sweep.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SWEEP_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splashHead",
    )
    val breath by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BREATHE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashBreath",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    val drift = (breath - 0.5f) * 2f
                    alpha = reveal
                    translationY = drift * 2.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                wordmark.forEachIndexed { index, letter ->
                    val position = if (wordmark.length > 1) {
                        index.toFloat() / (wordmark.length - 1)
                    } else {
                        0.5f
                    }
                    Text(
                        text = letter.toString(),
                        modifier = Modifier.graphicsLayer {
                            val distance = (position - head) / BAND
                            val glow = exp(-(distance * distance))
                            alpha = reveal * (DIM + (1f - DIM) * glow)
                            translationY = -(glow * 1.6f).dp.toPx()
                        },
                        color = Color.White,
                        fontFamily = DmMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 32.sp,
                        maxLines = 1,
                    )
                }
            }

            Text(
                text = version,
                color = EncoreColors.TextSecondary,
                fontFamily = DmMono,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                maxLines = 1,
            )
        }
    }
}

private fun buildLabel(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    val name = info.versionName.orEmpty()
    if (name.isBlank()) "build $code" else "v$name · build $code"
}.getOrDefault("")
