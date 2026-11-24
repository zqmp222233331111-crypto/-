package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.data.model.AccentColorPreset

@Immutable
data class ShailaExtendedColors(
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val userBubble: Color,
    val assistantBubble: Color,
    val success: Color,
    val warning: Color,
    val isDark: Boolean
)

val LocalShailaColors = staticCompositionLocalOf {
    ShailaExtendedColors(
        bgTop = CosmicBgTop,
        bgMid = CosmicBgMid,
        bgBottom = CosmicBgBottom,
        glassFill = DarkGlassFill,
        glassBorder = DarkGlassBorder,
        orbPrimary = AccentVioletPrimary,
        orbSecondary = AccentVioletSecondary,
        userBubble = Color(0xFF342B6E),
        assistantBubble = Color(0xAA172042),
        success = StatusSuccess,
        warning = StatusWarning,
        isDark = true
    )
}

private val ShailaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

fun accentPair(preset: AccentColorPreset): Pair<Color, Color> = when (preset) {
    AccentColorPreset.VIOLET -> AccentVioletPrimary to AccentVioletSecondary
    AccentColorPreset.CYAN -> AccentCyanPrimary to AccentCyanSecondary
    AccentColorPreset.ROSE -> AccentRosePrimary to AccentRoseSecondary
    AccentColorPreset.EMERALD -> AccentEmeraldPrimary to AccentEmeraldSecondary
    AccentColorPreset.AMBER -> AccentAmberPrimary to AccentAmberSecondary
}

@Composable
fun ShailaTheme(
    darkTheme: Boolean = true,
    accentPreset: AccentColorPreset = AccentColorPreset.VIOLET,
    content: @Composable () -> Unit
) {
    val (primaryAccent, secondaryAccent) = accentPair(accentPreset)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryAccent,
            onPrimary = Color.White,
            primaryContainer = primaryAccent.copy(alpha = 0.22f),
            onPrimaryContainer = Color.White,
            secondary = secondaryAccent,
            onSecondary = Color(0xFF04101E),
            secondaryContainer = secondaryAccent.copy(alpha = 0.2f),
            onSecondaryContainer = Color(0xFFE6F6FF),
            tertiary = Color(0xFFC084FC),
            background = CosmicBgTop,
            onBackground = DarkTextPrimary,
            surface = DarkSurface,
            onSurface = DarkTextPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkTextSecondary,
            outline = DarkGlassBorder,
            error = StatusError,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = primaryAccent,
            onPrimary = Color.White,
            primaryContainer = primaryAccent.copy(alpha = 0.14f),
            onPrimaryContainer = LightTextPrimary,
            secondary = secondaryAccent,
            onSecondary = Color.White,
            secondaryContainer = secondaryAccent.copy(alpha = 0.16f),
            onSecondaryContainer = LightTextPrimary,
            tertiary = Color(0xFF9333EA),
            background = LightBgTop,
            onBackground = LightTextPrimary,
            surface = LightSurface,
            onSurface = LightTextPrimary,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightTextSecondary,
            outline = LightGlassBorder,
            error = StatusError,
            onError = Color.White
        )
    }

    val extended = if (darkTheme) {
        ShailaExtendedColors(
            bgTop = CosmicBgTop,
            bgMid = CosmicBgMid,
            bgBottom = CosmicBgBottom,
            glassFill = DarkGlassFill,
            glassBorder = DarkGlassBorder,
            orbPrimary = primaryAccent,
            orbSecondary = secondaryAccent,
            userBubble = UserBubbleBlue,
            assistantBubble = AssistantBubblePurple,
            success = StatusSuccess,
            warning = StatusWarning,
            isDark = true
        )
    } else {
        ShailaExtendedColors(
            bgTop = LightBgTop,
            bgMid = LightBgMid,
            bgBottom = LightBgBottom,
            glassFill = LightGlassFill,
            glassBorder = LightGlassBorder,
            orbPrimary = primaryAccent,
            orbSecondary = secondaryAccent,
            userBubble = UserBubbleBlue.copy(alpha = 0.22f),
            assistantBubble = primaryAccent.copy(alpha = 0.14f),
            success = StatusSuccess,
            warning = StatusWarning,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalShailaColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ShailaTypography,
            shapes = ShailaShapes,
            content = content
        )
    }
}
