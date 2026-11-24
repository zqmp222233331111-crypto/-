package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ImageRegionRect
import com.example.ui.theme.AccentVioletBright
import com.example.ui.theme.AccentVioletPrimary
import com.example.ui.theme.AccentVioletSecondary
import com.example.ui.theme.LocalShailaColors
import com.example.ui.viewmodel.OrbState
import kotlin.math.sin

@Composable
fun AnimatedVoiceOrb(
    orbState: OrbState,
    rmsLevel: Float,
    avatarRegion: ImageRegionRect? = null,
    modifier: Modifier = Modifier,
    orbSize: Dp = 196.dp,
    onClick: () -> Unit = {}
) {
    val ext = LocalShailaColors.current
    val orbDescription = stringResource(R.string.cd_orb)
    val transition = rememberInfiniteTransition(label = "orb_transition")

    val breathe by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val rippleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    val animatedRms by animateFloatAsState(
        targetValue = if (orbState == OrbState.LISTENING) rmsLevel else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 320f),
        label = "rms_anim"
    )

    val primary = AccentVioletPrimary
    val brightPurple = AccentVioletBright
    val neonBlue = AccentVioletSecondary
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(orbSize)
            .clip(CircleShape)
            .semantics { contentDescription = orbDescription }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(orbSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f
            val avatarRadius = maxRadius * 0.68f

            val dynamicScale = when (orbState) {
                OrbState.IDLE -> breathe
                OrbState.LISTENING -> 1.0f + animatedRms * 0.14f + (breathe - 1f) * 0.4f
                OrbState.THINKING -> 0.99f + (breathe - 1f) * 0.25f
                OrbState.SPEAKING -> 1.02f + sin(rippleProgress * Math.PI * 2).toFloat() * 0.05f
            }
            val ringRadius = avatarRadius * dynamicScale

            // Soft neon blue-purple outer ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        neonBlue.copy(alpha = if (orbState == OrbState.IDLE) 0.34f else 0.52f),
                        brightPurple.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // Listening pulse rings
            if (orbState == OrbState.LISTENING) {
                val pulseR = ringRadius * (1.12f + animatedRms * 0.22f)
                drawCircle(
                    color = neonBlue.copy(alpha = 0.6f),
                    radius = pulseR,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = brightPurple.copy(alpha = 0.38f),
                    radius = pulseR * 1.12f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Speaking concentric wave ripples
            if (orbState == OrbState.SPEAKING) {
                for (i in 0 until 3) {
                    val phase = (rippleProgress + i / 3f) % 1f
                    val r = ringRadius + (maxRadius - ringRadius) * phase
                    val alpha = (1f - phase) * 0.7f
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(neonBlue.copy(alpha = alpha), brightPurple.copy(alpha = alpha), neonBlue.copy(alpha = alpha)),
                            center = center
                        ),
                        radius = r,
                        center = center,
                        style = Stroke(width = (3.5f - phase * 2f).dp.toPx())
                    )
                }
            }

            // Thinking rotating neon arcs
            if (orbState == OrbState.THINKING) {
                rotate(degrees = rotation, pivot = center) {
                    val arcR = ringRadius * 1.14f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color.Transparent, neonBlue, brightPurple, Color.Transparent),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 220f,
                        useCenter = false,
                        topLeft = Offset(center.x - arcR, center.y - arcR),
                        size = Size(arcR * 2, arcR * 2),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Main glowing neon blue-purple ring
            rotate(degrees = rotation * 0.35f, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(neonBlue, brightPurple, primary, neonBlue),
                        center = center
                    ),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 4.5.dp.toPx())
                )
            }
        }

        // Circular avatarFace cropped from the single collage asset
        val avatarDiameter = orbSize * 0.66f
        if (avatarRegion != null) {
            CroppedImage(
                region = avatarRegion,
                contentDescription = orbDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarDiameter)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(listOf(neonBlue, brightPurple)),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun SoundWaveBar(
    orbState: OrbState,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "wave_bar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val neonBlue = AccentVioletSecondary
    val brightPurple = AccentVioletBright

    Canvas(
        modifier = modifier
            .width(148.dp)
            .height(26.dp)
    ) {
        val barCount = 19
        val spacing = size.width / barCount
        val barWidth = (spacing * 0.48f).coerceAtLeast(2.dp.toPx())
        val activity = when (orbState) {
            OrbState.IDLE -> 0.25f
            OrbState.LISTENING -> 0.45f + rmsLevel * 0.55f
            OrbState.THINKING -> 0.55f
            OrbState.SPEAKING -> 0.92f
        }

        for (i in 0 until barCount) {
            val centerBias = 1f - kotlin.math.abs(i - barCount / 2f) / (barCount / 2f + 0.5f)
            val wave = kotlin.math.abs(sin(phase + i * 0.55f)).toFloat()
            val barH = (size.height * (0.18f + 0.80f * centerBias * wave * activity))
                .coerceIn(3.dp.toPx(), size.height)
            val x = i * spacing + (spacing - barWidth) / 2f
            val y = (size.height - barH) / 2f
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(neonBlue, brightPurple)),
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
