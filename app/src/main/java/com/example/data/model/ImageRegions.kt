package com.example.data.model

data class ImageRegionRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    fun clamped(): ImageRegionRect {
        val w = width.coerceIn(0.04f, 1.0f)
        val h = height.coerceIn(0.04f, 1.0f)
        val cx = x.coerceIn(0f, 1f - w)
        val cy = y.coerceIn(0f, 1f - h)
        return ImageRegionRect(cx, cy, w, h)
    }
}

enum class ImageRegionKey(val label: String, val description: String) {
    ICON_ART("iconArt", "Part A — App icon artwork (top-left)"),
    PORTRAIT_FACE("portraitFace", "Part B — Close-up portrait with hand on cheek"),
    FULL_BODY("fullBody", "Part C — Standing full-body in purple salwar-kameez"),
    AVATAR_FACE("avatarFace", "Part B tight — Square face & hair crop for circle avatar")
}

data class ImageRegionsConfig(
    val iconArt: ImageRegionRect = DefaultImageRegions.iconArt,
    val portraitFace: ImageRegionRect = DefaultImageRegions.portraitFace,
    val fullBody: ImageRegionRect = DefaultImageRegions.fullBody,
    val avatarFace: ImageRegionRect = DefaultImageRegions.avatarFace
) {
    fun get(key: ImageRegionKey): ImageRegionRect = when (key) {
        ImageRegionKey.ICON_ART -> iconArt
        ImageRegionKey.PORTRAIT_FACE -> portraitFace
        ImageRegionKey.FULL_BODY -> fullBody
        ImageRegionKey.AVATAR_FACE -> avatarFace
    }

    fun updated(key: ImageRegionKey, rect: ImageRegionRect): ImageRegionsConfig {
        val safe = rect.clamped()
        return when (key) {
            ImageRegionKey.ICON_ART -> copy(iconArt = safe)
            ImageRegionKey.PORTRAIT_FACE -> copy(portraitFace = safe)
            ImageRegionKey.FULL_BODY -> copy(fullBody = safe)
            ImageRegionKey.AVATAR_FACE -> copy(avatarFace = safe)
        }
    }
}

/**
 * Single centralized configuration for all percentage-based crop regions inside design_reference.png.
 * Coordinates are normalized (0f..1f) as percentages of full collage width & height.
 */
object DefaultImageRegions {
    val iconArt = ImageRegionRect(x = 0.008f, y = 0.032f, width = 0.268f, height = 0.402f)
    val portraitFace = ImageRegionRect(x = 0.275f, y = 0.005f, width = 0.320f, height = 0.460f)
    val fullBody = ImageRegionRect(x = 0.598f, y = 0.004f, width = 0.188f, height = 0.456f)
    val avatarFace = ImageRegionRect(x = 0.352f, y = 0.024f, width = 0.205f, height = 0.215f)

    val defaultConfig = ImageRegionsConfig(
        iconArt = iconArt,
        portraitFace = portraitFace,
        fullBody = fullBody,
        avatarFace = avatarFace
    )
}
