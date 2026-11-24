package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import com.example.data.model.ImageRegionRect
import kotlin.math.roundToInt

object CollageAssetCache {
    @Volatile
    private var cachedBitmap: ImageBitmap? = null

    fun getDesignReference(context: Context): ImageBitmap {
        cachedBitmap?.let { return it }
        synchronized(this) {
            cachedBitmap?.let { return it }
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val bmp = BitmapFactory.decodeResource(
                context.applicationContext.resources,
                R.drawable.design_reference,
                opts
            ).asImageBitmap()
            cachedBitmap = bmp
            return bmp
        }
    }
}

/**
 * Reusable CroppedImage helper that renders a single percentage-based region (x, y, width, height in 0f..1f)
 * from the single design_reference collage asset onto a Canvas at any container size.
 */
@Composable
fun CroppedImage(
    region: ImageRegionRect,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val collage = remember { CollageAssetCache.getDesignReference(context) }

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val safe = region.clamped()
            val imgW = collage.width.toFloat()
            val imgH = collage.height.toFloat()

            val srcX = (safe.x * imgW).roundToInt().coerceIn(0, collage.width - 1)
            val srcY = (safe.y * imgH).roundToInt().coerceIn(0, collage.height - 1)
            val srcW = (safe.width * imgW).roundToInt().coerceIn(1, collage.width - srcX)
            val srcH = (safe.height * imgH).roundToInt().coerceIn(1, collage.height - srcY)

            val canvasW = size.width
            val canvasH = size.height
            if (canvasW <= 0f || canvasH <= 0f) return@Canvas

            val srcAspect = srcW.toFloat() / srcH.toFloat()
            val dstAspect = canvasW / canvasH

            val (drawW, drawH) = when (contentScale) {
                ContentScale.Fit, ContentScale.Inside -> {
                    if (srcAspect > dstAspect) {
                        canvasW to (canvasW / srcAspect)
                    } else {
                        (canvasH * srcAspect) to canvasH
                    }
                }
                ContentScale.FillBounds -> canvasW to canvasH
                else -> { // Crop / FillWidth / FillHeight
                    if (srcAspect > dstAspect) {
                        (canvasH * srcAspect) to canvasH
                    } else {
                        canvasW to (canvasW / srcAspect)
                    }
                }
            }

            val dstX = ((canvasW - drawW) / 2f).roundToInt()
            val dstY = ((canvasH - drawH) / 2f).roundToInt()

            drawImage(
                image = collage,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset(dstX, dstY),
                dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1))
            )
        }
    }
}
