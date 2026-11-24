package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ImageRegionKey
import com.example.data.model.ImageRegionRect
import com.example.data.model.ImageRegionsConfig
import com.example.ui.components.CollageAssetCache
import com.example.ui.components.CroppedImage
import com.example.ui.components.GlassCard
import kotlin.math.hypot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageRegionDebugScreen(
    regions: ImageRegionsConfig,
    onUpdateRegion: (ImageRegionKey, ImageRegionRect) -> Unit,
    onResetAll: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val collage = remember { CollageAssetCache.getDesignReference(context) }
    val collageAspect = collage.width.toFloat() / collage.height.toFloat()

    var activeKey by remember { mutableStateOf(ImageRegionKey.AVATAR_FACE) }
    val activeRect = regions.get(activeKey)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("debug_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "Image Region Debug",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Drag box or corner handle • auto-saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(onClick = onResetAll) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImageRegionKey.entries.forEach { key ->
                FilterChip(
                    selected = activeKey == key,
                    onClick = { activeKey = key },
                    label = { Text(key.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Collage Editor with draggable & resizable active crop box
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            contentPadding = 10.dp
        ) {
            Text(
                text = activeKey.description,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(6.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(collageAspect)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val boxW = constraints.maxWidth.toFloat()
                val boxH = constraints.maxHeight.toFloat()

                Image(
                    bitmap = collage,
                    contentDescription = "Design reference collage",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                var dragMode by remember { mutableStateOf(0) } // 1 = move, 2 = resize bottom-right

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeKey, regions) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val r = regions.get(activeKey)
                                    val handleX = (r.x + r.width) * boxW
                                    val handleY = (r.y + r.height) * boxH
                                    val distToHandle = hypot(start.x - handleX, start.y - handleY)
                                    dragMode = if (distToHandle < 64f) 2 else 1
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val cur = regions.get(activeKey)
                                    val dx = dragAmount.x / boxW
                                    val dy = dragAmount.y / boxH
                                    val updated = if (dragMode == 2) {
                                        cur.copy(
                                            width = (cur.width + dx).coerceIn(0.05f, 1f - cur.x),
                                            height = (cur.height + dy).coerceIn(0.05f, 1f - cur.y)
                                        )
                                    } else {
                                        cur.copy(
                                            x = (cur.x + dx).coerceIn(0f, 1f - cur.width),
                                            y = (cur.y + dy).coerceIn(0f, 1f - cur.height)
                                        )
                                    }
                                    onUpdateRegion(activeKey, updated)
                                }
                            )
                        }
                ) {
                    ImageRegionKey.entries.forEach { key ->
                        val r = regions.get(key)
                        val isActive = key == activeKey
                        val topLeft = Offset(r.x * size.width, r.y * size.height)
                        val rectSize = Size(r.width * size.width, r.height * size.height)
                        drawRect(
                            color = if (isActive) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.45f),
                            topLeft = topLeft,
                            size = rectSize,
                            style = Stroke(width = if (isActive) 3.dp.toPx() else 1.5.dp.toPx())
                        )
                        if (isActive) {
                            val br = Offset(topLeft.x + rectSize.width, topLeft.y + rectSize.height)
                            drawCircle(color = Color(0xFFA855F7), radius = 10.dp.toPx(), center = br)
                            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = br)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "x=${"%.3f".format(activeRect.x)}  y=${"%.3f".format(activeRect.y)}  w=${"%.3f".format(activeRect.width)}  h=${"%.3f".format(activeRect.height)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            RegionSlider("X (%)", activeRect.x, 0f..(1f - activeRect.width).coerceAtLeast(0.01f)) {
                onUpdateRegion(activeKey, activeRect.copy(x = it))
            }
            RegionSlider("Y (%)", activeRect.y, 0f..(1f - activeRect.height).coerceAtLeast(0.01f)) {
                onUpdateRegion(activeKey, activeRect.copy(y = it))
            }
            RegionSlider("Width (%)", activeRect.width, 0.05f..(1f - activeRect.x).coerceAtLeast(0.06f)) {
                onUpdateRegion(activeKey, activeRect.copy(width = it))
            }
            RegionSlider("Height (%)", activeRect.height, 0.05f..(1f - activeRect.y).coerceAtLeast(0.06f)) {
                onUpdateRegion(activeKey, activeRect.copy(height = it))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Cropped Previews of all 4 regions
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
        ) {
            Text(
                text = "Live Cropped Previews",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewTile("iconArt", regions.iconArt, isCircle = false)
                PreviewTile("portraitFace", regions.portraitFace, isCircle = false)
                PreviewTile("fullBody", regions.fullBody, isCircle = false)
                PreviewTile("avatarFace", regions.avatarFace, isCircle = true)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Done (Saved Permanently)")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RegionSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(72.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PreviewTile(
    label: String,
    rect: ImageRegionRect,
    isCircle: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CroppedImage(
            region = rect,
            contentDescription = label,
            contentScale = if (isCircle) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier
                .size(72.dp)
                .clip(if (isCircle) CircleShape else RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.4f), if (isCircle) CircleShape else RoundedCornerShape(14.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
