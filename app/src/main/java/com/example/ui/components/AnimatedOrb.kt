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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
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
    speakingLevel: Float = 0f,
    avatarRegion: ImageRegionRect? = null,
    modifier: Modifier = Modifier,
    orbSize: Dp = 196.dp,
    onClick: () -> Unit = {}
) {
    LocalShailaColors.current
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

    val blinkClock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blink_clock"
    )

    val animatedRms by animateFloatAsState(
        targetValue = if (orbState == OrbState.LISTENING) rmsLevel else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 320f),
        label = "rms_anim"
    )

    val speechWaveFallback = if (orbState == OrbState.SPEAKING) {
        0.22f + 0.18f * kotlin.math.abs(sin(rippleProgress * Math.PI * 6)).toFloat()
    } else {
        0f
    }
    val targetSpeak = if (orbState == OrbState.SPEAKING || speakingLevel > 0.04f) {
        speakingLevel.coerceAtLeast(speechWaveFallback).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedSpeaking by animateFloatAsState(
        targetValue = targetSpeak,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 460f),
        label = "speak_anim"
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
                OrbState.SPEAKING -> 1.02f + animatedSpeaking * 0.07f + sin(rippleProgress * Math.PI * 2).toFloat() * 0.02f
            }
            val ringRadius = avatarRadius * dynamicScale

            // Soft neon blue-purple outer ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        neonBlue.copy(alpha = if (orbState == OrbState.IDLE) 0.34f else 0.55f),
                        brightPurple.copy(alpha = 0.24f),
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
                    val alpha = (1f - phase) * (0.45f + 0.35f * animatedSpeaking)
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

        // Circular avatarFace cropped from the single collage asset with subtle head motion + 2D lip-sync
        val avatarDiameter = orbSize * 0.66f
        if (avatarRegion != null) {
            val headScale = if (orbState == OrbState.SPEAKING) {
                1.0f + animatedSpeaking * 0.032f
            } else {
                0.985f + (breathe - 0.96f) * 0.25f
            }
            val headTilt = if (orbState == OrbState.SPEAKING) {
                sin(rippleProgress * Math.PI * 2).toFloat() * (1.2f * animatedSpeaking)
            } else {
                0f
            }
            val blinkAmount = if (blinkClock in 0.93f..0.97f) {
                1f - kotlin.math.abs(blinkClock - 0.95f) / 0.02f
            } else {
                0f
            }

            Box(
                modifier = Modifier
                    .size(avatarDiameter)
                    .graphicsLayer {
                        scaleX = headScale
                        scaleY = headScale
                        rotationZ = headTilt
                    }
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(listOf(neonBlue, brightPurple)),
                        shape = CircleShape
                    )
            ) {
                CroppedImage(
                    region = avatarRegion,
                    contentDescription = orbDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(avatarDiameter)
                )

                // Facial life overlay: natural periodic blink + speech-reactive lip-sync mouth
                Canvas(modifier = Modifier.size(avatarDiameter)) {
                    // 1. Subtle natural blink
                    if (blinkAmount > 0.08f) {
                        val lidAlpha = 0.62f * blinkAmount
                        val eyeY = size.height * 0.425f
                        val eyeW = size.width * 0.115f
                        val eyeH = size.height * 0.048f * blinkAmount
                        drawOval(
                            color = Color(0xFFEEC5B4).copy(alpha = lidAlpha),
                            topLeft = Offset(size.width * 0.34f, eyeY - eyeH / 2f),
                            size = Size(eyeW, eyeH)
                        )
                        drawOval(
                            color = Color(0xFFEEC5B4).copy(alpha = lidAlpha),
                            topLeft = Offset(size.width * 0.545f, eyeY - eyeH / 2f),
                            size = Size(eyeW, eyeH)
                        )
                    }

                    // 2. Dynamic Lip-Sync Mouth Overlay synchronized to speakingLevel
                    if (animatedSpeaking > 0.03f) {
                        val mouthCenterX = size.width * 0.505f
                        val mouthCenterY = size.height * 0.652f
                        val mouthWidth = size.width * (0.105f + animatedSpeaking * 0.032f)
                        val mouthHeight = size.height * (0.014f + animatedSpeaking * 0.062f)

                        // Soft warm lower-lip contour glow
                        drawOval(
                            color = Color(0xFFD56B84).copy(alpha = 0.70f),
                            topLeft = Offset(
                                mouthCenterX - mouthWidth * 0.58f,
                                mouthCenterY - mouthHeight * 0.58f
                            ),
                            size = Size(mouthWidth * 1.16f, mouthHeight * 1.24f)
                        )

                        // Open mouth cavity shaped naturally like curved lips
                        val path = Path().apply {
                            moveTo(mouthCenterX - mouthWidth / 2f, mouthCenterY)
                            quadraticTo(
                                mouthCenterX,
                                mouthCenterY - mouthHeight * 0.65f,
                                mouthCenterX + mouthWidth / 2f,
                                mouthCenterY
                            )
                            quadraticTo(
                                mouthCenterX,
                                mouthCenterY + mouthHeight * 0.85f,
                                mouthCenterX - mouthWidth / 2f,
                                mouthCenterY
                            )
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF4B1328).copy(alpha = 0.88f)
                        )

                        // Subtle upper-teeth glint when mouth opens wider
                        if (animatedSpeaking > 0.36f) {
                            val teethAlpha = ((animatedSpeaking - 0.36f) * 1.2f).coerceIn(0f, 0.72f)
                            drawRoundRect(
                                color = Color(0xFFFFF5F5).copy(alpha = teethAlpha),
                                topLeft = Offset(
                                    mouthCenterX - mouthWidth * 0.28f,
                                    mouthCenterY - mouthHeight * 0.24f
                                ),
                                size = Size(mouthWidth * 0.56f, mouthHeight * 0.24f),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )
                        }
                    }
                }
            }
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
