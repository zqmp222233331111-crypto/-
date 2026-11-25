package com.example.ui.components

import android.net.Uri
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.HomeCharacterStyle
import com.example.data.model.ImageRegionRect
import com.example.ui.theme.AccentVioletBright
import com.example.ui.theme.AccentVioletPrimary
import com.example.ui.theme.AccentVioletSecondary
import com.example.ui.viewmodel.OrbState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val PORTRAIT_ASPECT_RATIO = 941f / 1672f

@Composable
fun LivingHalfBodyCharacter(
    homeCharacterStyle: HomeCharacterStyle,
    customCharacterUri: String,
    orbState: OrbState,
    rmsLevel: Float,
    speakingLevel: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val description = stringResource(R.string.cd_orb)
    val transition = rememberInfiniteTransition(label = "living_character")

    val breatheClock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_clock"
    )

    val gesturePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gesture_phase"
    )

    val blinkClock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blink_clock"
    )

    val animatedRms by animateFloatAsState(
        targetValue = if (orbState == OrbState.LISTENING) rmsLevel else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 320f),
        label = "char_rms"
    )

    val speechFallback = if (orbState == OrbState.SPEAKING) {
        0.24f + 0.22f * abs(sin(gesturePhase * 2.5f))
    } else {
        0f
    }
    val targetSpeak = if (orbState == OrbState.SPEAKING || speakingLevel > 0.04f) {
        speakingLevel.coerceAtLeast(speechFallback).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedSpeaking by animateFloatAsState(
        targetValue = targetSpeak,
        animationSpec = spring(dampingRatio = 0.56f, stiffness = 460f),
        label = "char_speak"
    )

    val motionFactor = if (reduceMotion) 0.15f else 1.0f
    val isBuiltInPortrait = homeCharacterStyle != HomeCharacterStyle.CUSTOM_GALLERY || customCharacterUri.isBlank()

    // Subtle torso breathing + speaking head/body sway
    val breatheScale = 1.0f + ((breatheClock - 0.5f) * 0.016f + animatedSpeaking * 0.022f) * motionFactor
    val swayDegrees = when (orbState) {
        OrbState.SPEAKING -> sin(gesturePhase) * (1.8f * (0.4f + animatedSpeaking)) * motionFactor
        OrbState.LISTENING -> sin(gesturePhase * 0.5f) * 1.1f * motionFactor
        OrbState.THINKING -> sin(gesturePhase * 0.4f) * 1.4f * motionFactor
        OrbState.IDLE -> (breatheClock - 0.5f) * 0.9f * motionFactor
    }
    val headNodPx = when (orbState) {
        OrbState.SPEAKING -> -sin(gesturePhase * 2f) * (5f * (0.3f + animatedSpeaking)) * motionFactor
        OrbState.LISTENING -> -animatedRms * 6f * motionFactor
        else -> (breatheClock - 0.5f) * -4f * motionFactor
    }
    val yawDegrees = if (orbState == OrbState.SPEAKING) {
        cos(gesturePhase * 0.8f) * (2.6f * (0.35f + animatedSpeaking)) * motionFactor
    } else {
        0f
    }

    val blinkAmount = if (blinkClock in 0.93f..0.975f) {
        1f - abs(blinkClock - 0.9525f) / 0.0225f
    } else {
        0f
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .testTag("living_character_view")
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Cosmic ambient aura halo behind the floating half-body portrait
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.48f)
            val glowRadius = size.minDimension * 0.72f
            val auraIntensity = when (orbState) {
                OrbState.SPEAKING -> 0.52f + animatedSpeaking * 0.35f
                OrbState.LISTENING -> 0.46f + animatedRms * 0.32f
                OrbState.THINKING -> 0.42f
                OrbState.IDLE -> 0.28f + breatheClock * 0.08f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentVioletSecondary.copy(alpha = auraIntensity),
                        AccentVioletBright.copy(alpha = auraIntensity * 0.55f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            if (orbState == OrbState.LISTENING || orbState == OrbState.SPEAKING) {
                val pulseRadius = glowRadius * (0.64f + (if (orbState == OrbState.SPEAKING) animatedSpeaking else animatedRms) * 0.14f)
                drawCircle(
                    color = AccentVioletSecondary.copy(alpha = 0.28f),
                    radius = pulseRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Exact aspect-locked portrait container with feathered cosmic vignette fade
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(PORTRAIT_ASPECT_RATIO, matchHeightConstraintsFirst = true)
                .graphicsLayer {
                    scaleX = breatheScale
                    scaleY = breatheScale
                    translationY = headNodPx
                    rotationZ = swayDegrees
                    rotationY = yawDegrees
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // Feather top corners, sides gently, and bottom into transparent so she blends organically
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.04f to Color.Black,
                            0.78f to Color.Black,
                            0.98f to Color.Transparent,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.0f to Color.Transparent,
                            0.06f to Color.Black,
                            0.94f to Color.Black,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
        ) {
            if (isBuiltInPortrait) {
                Image(
                    painter = painterResource(id = R.drawable.shaila_character_portrait),
                    contentDescription = description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Calibrated eye blink + lip-sync mouth canvas mapped strictly to shaila_character_portrait
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 1. Natural periodic eye blink (left eye 0.445,0.278 ; right eye 0.595,0.271)
                    if (blinkAmount > 0.08f) {
                        val lidAlpha = 0.72f * blinkAmount
                        val eyeW = size.width * 0.105f
                        val eyeH = size.height * 0.032f * blinkAmount

                        val leftCx = size.width * 0.445f
                        val leftCy = size.height * 0.278f
                        drawOval(
                            color = Color(0xFFF3C6B4).copy(alpha = lidAlpha),
                            topLeft = Offset(leftCx - eyeW / 2f, leftCy - eyeH / 2f),
                            size = Size(eyeW, eyeH)
                        )
                        drawLine(
                            color = Color(0xFF28161C).copy(alpha = lidAlpha * 0.9f),
                            start = Offset(leftCx - eyeW * 0.45f, leftCy + eyeH * 0.1f),
                            end = Offset(leftCx + eyeW * 0.45f, leftCy + eyeH * 0.1f),
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val rightCx = size.width * 0.595f
                        val rightCy = size.height * 0.271f
                        drawOval(
                            color = Color(0xFFF3C6B4).copy(alpha = lidAlpha),
                            topLeft = Offset(rightCx - eyeW / 2f, rightCy - eyeH / 2f),
                            size = Size(eyeW, eyeH)
                        )
                        drawLine(
                            color = Color(0xFF28161C).copy(alpha = lidAlpha * 0.9f),
                            start = Offset(rightCx - eyeW * 0.45f, rightCy + eyeH * 0.1f),
                            end = Offset(rightCx + eyeW * 0.45f, rightCy + eyeH * 0.1f),
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // 2. Dynamic Lip-Sync Mouth Overlay synchronized to speakingLevel (center 0.530, 0.365)
                    if (animatedSpeaking > 0.03f) {
                        val mouthCenterX = size.width * 0.530f
                        val mouthCenterY = size.height * 0.365f
                        val mouthWidth = size.width * (0.095f + animatedSpeaking * 0.025f)
                        val mouthHeight = size.height * (0.008f + animatedSpeaking * 0.034f)

                        drawOval(
                            color = Color(0xFFE0627F).copy(alpha = 0.68f),
                            topLeft = Offset(
                                mouthCenterX - mouthWidth * 0.58f,
                                mouthCenterY - mouthHeight * 0.58f
                            ),
                            size = Size(mouthWidth * 1.16f, mouthHeight * 1.24f)
                        )

                        val mouthPath = Path().apply {
                            moveTo(mouthCenterX - mouthWidth / 2f, mouthCenterY)
                            quadraticTo(
                                mouthCenterX,
                                mouthCenterY - mouthHeight * 0.62f,
                                mouthCenterX + mouthWidth / 2f,
                                mouthCenterY
                            )
                            quadraticTo(
                                mouthCenterX,
                                mouthCenterY + mouthHeight * 0.88f,
                                mouthCenterX - mouthWidth / 2f,
                                mouthCenterY
                            )
                            close()
                        }
                        drawPath(
                            path = mouthPath,
                            color = Color(0xFF4A1023).copy(alpha = 0.88f)
                        )

                        if (animatedSpeaking > 0.34f) {
                            val teethAlpha = ((animatedSpeaking - 0.34f) * 1.25f).coerceIn(0f, 0.72f)
                            drawRoundRect(
                                color = Color(0xFFFFF5F5).copy(alpha = teethAlpha),
                                topLeft = Offset(
                                    mouthCenterX - mouthWidth * 0.28f,
                                    mouthCenterY - mouthHeight * 0.22f
                                ),
                                size = Size(mouthWidth * 0.56f, mouthHeight * 0.24f),
                                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                            )
                        }
                    }
                }
            } else {
                AsyncImage(
                    model = Uri.parse(customCharacterUri),
                    contentDescription = description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Expressive feathered lower-arm / hands gesture layer (visible only smoothly when active)
            if (!reduceMotion && (orbState == OrbState.SPEAKING || orbState == OrbState.LISTENING)) {
                val gestureAmplitude = if (orbState == OrbState.SPEAKING) (0.35f + animatedSpeaking) else (0.18f + animatedRms * 0.4f)
                val handShiftY = -sin(gesturePhase * 1.5f) * 9f * gestureAmplitude
                val handShiftX = cos(gesturePhase * 0.9f) * 4f * gestureAmplitude
                val handRotate = sin(gesturePhase * 0.85f) * 2.4f * gestureAmplitude

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = handShiftY
                            translationX = handShiftX
                            rotationZ = handRotate
                            scaleX = 1.015f
                            scaleY = 1.015f
                            alpha = (0.72f * gestureAmplitude).coerceIn(0f, 0.85f)
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.60f to Color.Transparent,
                                    0.73f to Color.Black,
                                    0.94f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    if (isBuiltInPortrait) {
                        Image(
                            painter = painterResource(id = R.drawable.shaila_character_portrait),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = Uri.parse(customCharacterUri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShailaCharacterAvatar(
    homeCharacterStyle: HomeCharacterStyle,
    customCharacterUri: String,
    fallbackAvatarRegion: ImageRegionRect,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    borderColor: Color = AccentVioletBright
) {
    val clippedModifier = modifier
        .clip(CircleShape)
        .border(1.5.dp, borderColor, CircleShape)

    when {
        homeCharacterStyle == HomeCharacterStyle.CUSTOM_GALLERY && customCharacterUri.isNotBlank() -> {
            AsyncImage(
                model = Uri.parse(customCharacterUri),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = clippedModifier
            )
        }
        homeCharacterStyle == HomeCharacterStyle.HALF_BODY_SHAILA || homeCharacterStyle == HomeCharacterStyle.CUSTOM_GALLERY -> {
            Box(modifier = clippedModifier, contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.shaila_character_portrait),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Focus zoom cleanly on her smiling face & butterfly clip
                            scaleX = 2.05f
                            scaleY = 2.05f
                            translationY = size.height * 0.40f
                            translationX = -size.width * 0.05f
                        }
                )
            }
        }
        else -> {
            CroppedImage(
                region = fallbackAvatarRegion,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = clippedModifier
            )
        }
    }
}
