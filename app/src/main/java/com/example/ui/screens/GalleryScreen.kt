package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.media.VideoFrameExtractor
import com.example.data.model.AppLanguage
import com.example.data.model.GalleryMediaItem
import com.example.ui.components.GlassCard
import com.example.ui.theme.AccentVioletBright
import com.example.ui.theme.AccentVioletPrimary
import com.example.ui.theme.LocalShailaColors
import com.example.ui.viewmodel.GalleryUiState
import com.example.ui.viewmodel.OrbState

private enum class GalleryFilter { PHOTOS, VIDEOS }

@Composable
fun GalleryScreen(
    appLanguage: AppLanguage,
    mediaItems: List<GalleryMediaItem>,
    galleryState: GalleryUiState,
    orbState: OrbState,
    partialTranscript: String,
    frameExtractor: VideoFrameExtractor,
    onAddUris: (List<Uri>) -> Unit,
    onRemoveItem: (String) -> Unit,
    onSelectItem: (GalleryMediaItem) -> Unit,
    onClearSelection: () -> Unit,
    onAnalyzeMedia: (String, AppLanguage) -> Unit,
    onStartVoiceForGallery: (GalleryMediaItem) -> Unit,
    onStopVoice: () -> Unit,
    onSpeakText: (String) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val isBn = appLanguage == AppLanguage.BENGALI
    val ext = LocalShailaColors.current
    var activeFilter by rememberSaveable { mutableStateOf(GalleryFilter.PHOTOS) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddUris(uris)
        }
    }

    val openMediaPicker = {
        pickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    val selected = galleryState.selectedItem
    if (selected != null) {
        GalleryDetailView(
            item = selected,
            galleryState = galleryState,
            appLanguage = appLanguage,
            orbState = orbState,
            partialTranscript = partialTranscript,
            onBack = onClearSelection,
            onAnalyze = onAnalyzeMedia,
            onStartVoice = { onStartVoiceForGallery(selected) },
            onStopVoice = onStopVoice,
            onSpeakText = onSpeakText
        )
        return
    }

    val photosCount = mediaItems.count { !it.isVideo }
    val videosCount = mediaItems.count { it.isVideo }

    // Auto-switch if one tab is empty and the other has items
    LaunchedEffect(photosCount, videosCount) {
        if (activeFilter == GalleryFilter.PHOTOS && photosCount == 0 && videosCount > 0) {
            activeFilter = GalleryFilter.VIDEOS
        } else if (activeFilter == GalleryFilter.VIDEOS && videosCount == 0 && photosCount > 0) {
            activeFilter = GalleryFilter.PHOTOS
        }
    }

    val filteredItems = remember(mediaItems, activeFilter) {
        when (activeFilter) {
            GalleryFilter.PHOTOS -> mediaItems.filter { !it.isVideo }
            GalleryFilter.VIDEOS -> mediaItems.filter { it.isVideo }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header matching Mockup F: Photo icon left, "My Gallery" center, Settings right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 800.dp)
                .padding(top = 8.dp, bottom = 10.dp)
        ) {
            IconButton(
                onClick = openMediaPicker,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag("gallery_add_media_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = if (isBn) "ছবি বা ভিডিও যোগ করুন" else "Add Photos or Videos",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = if (isBn) "My Gallery" else "My Gallery",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = if (isBn) "সেটিংস" else "Settings",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Pill Tabs: "Photos" and "Videos"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 800.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GalleryPillTab(
                label = if (isBn) "Photos ($photosCount)" else "Photos ($photosCount)",
                selected = activeFilter == GalleryFilter.PHOTOS,
                onClick = { activeFilter = GalleryFilter.PHOTOS },
                modifier = Modifier.weight(1f)
            )
            GalleryPillTab(
                label = if (isBn) "Videos ($videosCount)" else "Videos ($videosCount)",
                selected = activeFilter == GalleryFilter.VIDEOS,
                onClick = { activeFilter = GalleryFilter.VIDEOS },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3-Column Grid of the phone's real photos/videos
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            tint = AccentVioletBright,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isBn) "গ্যালারি থেকে ছবি বা ভিডিও বেছে নিন" else "Pick Photos or Videos to Begin",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isBn) {
                                "ফোনের ছবি বা ভিডিও যোগ করুন। শাইলা ফ্রেম বিশ্লেষণ করে টাইটেল, ডেসক্রিপশন, ট্যাগ ও ক্যাপশন লিখে দেবে।"
                            } else {
                                "Select videos or photos privately via Android Photo Picker. Shaila extracts frames and generates titles, tags, and captions."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = openMediaPicker,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("gallery_empty_pick_button")
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isBn) "ছবি ও ভিডিও বাছাই করুন" else "Choose Photos & Videos")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredItems, key = { it.uri }) { item ->
                    GalleryGridTile(
                        item = item,
                        frameExtractor = frameExtractor,
                        onSelect = { onSelectItem(item) },
                        onRemove = { onRemoveItem(item.uri) }
                    )
                }
            }
        }

        // Bottom Glass Card: "Ask about your photo or video" with hint "এই ছবিটা সম্পর্কে বলো"
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 800.dp)
                .padding(bottom = 10.dp),
            cornerRadius = 22.dp,
            contentPadding = 14.dp,
            onClick = {
                val target = filteredItems.firstOrNull() ?: mediaItems.firstOrNull()
                if (target != null) {
                    onSelectItem(target)
                } else {
                    openMediaPicker()
                }
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ask about your photo or video",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "“এই ছবিটা সম্পর্কে বলো”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(listOf(AccentVioletPrimary, AccentVioletBright))
                        )
                        .clickable { openMediaPicker() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = if (isBn) "গ্যালারি থেকে বাছাই করুন" else "Pick media",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryPillTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalShailaColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) AccentVioletBright else ext.glassBorder
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (selected) {
                        Brush.horizontalGradient(listOf(AccentVioletPrimary, AccentVioletBright))
                    } else {
                        Brush.horizontalGradient(listOf(ext.glassFill, ext.glassFill))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected || ext.isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GalleryGridTile(
    item: GalleryMediaItem,
    frameExtractor: VideoFrameExtractor,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val ext = LocalShailaColors.current
    var videoThumb by remember(item.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.uri, item.isVideo) {
        if (item.isVideo) {
            videoThumb = frameExtractor.loadVideoThumbnail(Uri.parse(item.uri))
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect),
        color = ext.glassFill,
        border = BorderStroke(1.dp, ext.glassBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.isVideo && videoThumb != null) {
                Image(
                    bitmap = videoThumb!!.asImageBitmap(),
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                AsyncImage(
                    model = Uri.parse(item.uri),
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (item.isVideo) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    color = Color.Black.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatDuration(item.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove from grid",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun GalleryDetailView(
    item: GalleryMediaItem,
    galleryState: GalleryUiState,
    appLanguage: AppLanguage,
    orbState: OrbState,
    partialTranscript: String,
    onBack: () -> Unit,
    onAnalyze: (String, AppLanguage) -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onSpeakText: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isBn = appLanguage == AppLanguage.BENGALI
    var questionText by rememberSaveable { mutableStateOf("") }
    var outputLang by rememberSaveable { mutableStateOf(appLanguage) }
    var copiedFeedback by remember { mutableStateOf<String?>(null) }

    var titleChecked by remember { mutableStateOf(false) }
    var descChecked by remember { mutableStateOf(false) }
    var tagsChecked by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartVoice()
    }

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
                .widthIn(max = 720.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("gallery_detail_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isBn) "পেছনে" else "Back"
                )
            }
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
        ) {
            if (item.isVideo) {
                Text(
                    text = if (isBn) "ভিডিও থেকে তোলা ফ্রেমসমূহ" else "Extracted Video Frames",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (galleryState.isExtractingFrames) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBn) "ফ্রেম বের করা হচ্ছে..." else "Extracting frames...")
                    }
                } else if (galleryState.extractedFrames.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(galleryState.extractedFrames) { frame ->
                            Image(
                                bitmap = frame.bitmap.asImageBitmap(),
                                contentDescription = "Video frame",
                                modifier = Modifier
                                    .size(width = 128.dp, height = 84.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            } else {
                AsyncImage(
                    model = Uri.parse(item.uri),
                    contentDescription = item.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "আউটপুট ভাষা:" else "Output:",
                    style = MaterialTheme.typography.labelMedium
                )
                FilterChip(
                    selected = outputLang == AppLanguage.BENGALI,
                    onClick = { outputLang = AppLanguage.BENGALI },
                    label = { Text("বাংলা") }
                )
                FilterChip(
                    selected = outputLang == AppLanguage.ENGLISH,
                    onClick = { outputLang = AppLanguage.ENGLISH },
                    label = { Text("English") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (partialTranscript.isNotBlank()) {
                Text(
                    text = partialTranscript,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (isBn) "এই ছবি/ভিডিও সম্পর্কে বলো (ঐচ্ছিক)" else "Ask something about this media (optional)"
                        )
                    },
                    maxLines = 2
                )
                FilledTonalIconButton(
                    onClick = {
                        if (orbState == OrbState.LISTENING) {
                            onStopVoice()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) onStartVoice()
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (orbState == OrbState.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isBn) "ভয়েসে জিজ্ঞাসা করুন" else "Ask by voice"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onAnalyze(questionText, outputLang) },
                enabled = !galleryState.isAnalyzing && !galleryState.isExtractingFrames,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("gallery_analyze_button")
            ) {
                if (galleryState.isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isBn) "শাইলা বিশ্লেষণ করছে..." else "Analyzing...")
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isBn) "বিশ্লেষণ, টাইটেল ও ক্যাপশন তৈরি করুন" else "Analyze & Generate Creator Copy"
                    )
                }
            }
        }

        if (galleryState.errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            ) {
                Text(
                    text = galleryState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        val analysis = galleryState.analysisResult
        if (analysis != null) {
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) "এআই বিশ্লেষণ ফলাফল" else "AI Analysis Result",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { onSpeakText(analysis.rawSummary) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isBn) "শুনুন" else "Speak summary"
                        )
                    }
                }

                Text(analysis.rawSummary, style = MaterialTheme.typography.bodyLarge)

                if (analysis.title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isBn) "প্রস্তাবিত টাইটেল" else "Suggested Title",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(analysis.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }

                if (analysis.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isBn) "ডেসক্রিপশন" else "Description",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(analysis.description, style = MaterialTheme.typography.bodyMedium)
                }

                if (analysis.tags.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isBn) "ট্যাগসমূহ" else "Tags",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(analysis.tags, style = MaterialTheme.typography.bodySmall)
                }

                if (analysis.caption.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isBn) "সোশ্যাল ক্যাপশন" else "Social Caption",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(analysis.caption, style = MaterialTheme.typography.bodyMedium)
                }

                if (copiedFeedback != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = copiedFeedback!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(analysis.title))
                            titleChecked = true
                            copiedFeedback = if (isBn) "টাইটেল কপি হয়েছে!" else "Title copied!"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBn) "টাইটেল" else "Copy Title", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val fullDesc = "${analysis.description}\n\nTags: ${analysis.tags}"
                            clipboard.setText(AnnotatedString(fullDesc))
                            descChecked = true
                            tagsChecked = true
                            copiedFeedback = if (isBn) "ডেসক্রিপশন কপি হয়েছে!" else "Description copied!"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBn) "ডেসক্রিপশন" else "Copy Desc", maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(analysis.caption))
                            copiedFeedback = if (isBn) "ক্যাপশন কপি হয়েছে!" else "Caption copied!"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBn) "ক্যাপশন" else "Copy Caption", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = {
                            val shareText = buildString {
                                if (analysis.title.isNotBlank()) appendLine(analysis.title)
                                if (analysis.caption.isNotBlank()) appendLine("\n${analysis.caption}")
                                if (analysis.tags.isNotBlank()) appendLine("\n${analysis.tags}")
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = if (item.isVideo) "video/*" else "image/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    if (isBn) "শেয়ার করুন" else "Share Media"
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isBn) "শেয়ার" else "Share", maxLines = 1)
                    }
                }
            }

            if (item.isVideo) {
                Spacer(modifier = Modifier.height(12.dp))
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                ) {
                    Text(
                        text = if (isBn) "ইউটিউব আপলোড হেল্পার" else "YouTube Upload Helper",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ChecklistItem(
                        checked = true,
                        label = if (isBn) "ভিডিও নির্বাচিত: ${item.displayName}" else "Video selected: ${item.displayName}",
                        onToggle = {}
                    )
                    ChecklistItem(
                        checked = titleChecked,
                        label = if (isBn) "টাইটেল কপি করুন ও যাচাই করুন" else "Copy & verify title",
                        onToggle = {
                            clipboard.setText(AnnotatedString(analysis.title))
                            titleChecked = !titleChecked
                        }
                    )
                    ChecklistItem(
                        checked = descChecked,
                        label = if (isBn) "ডেসক্রিপশন কপি করুন" else "Copy description",
                        onToggle = {
                            clipboard.setText(AnnotatedString(analysis.description))
                            descChecked = !descChecked
                        }
                    )
                    ChecklistItem(
                        checked = tagsChecked,
                        label = if (isBn) "ট্যাগ কপি করুন" else "Copy tags",
                        onToggle = {
                            clipboard.setText(AnnotatedString(analysis.tags))
                            tagsChecked = !tagsChecked
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val combinedMeta = "${analysis.title}\n\n${analysis.description}\n\n${analysis.tags}"
                            clipboard.setText(AnnotatedString(combinedMeta))
                            titleChecked = true
                            descChecked = true
                            tagsChecked = true
                            val ytIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                                putExtra(Intent.EXTRA_SUBJECT, analysis.title)
                                putExtra(Intent.EXTRA_TEXT, "${analysis.description}\n\n${analysis.tags}")
                                setPackage("com.google.android.youtube")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(ytIntent)
                            }.onFailure {
                                val fallback = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/*"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                                    putExtra(Intent.EXTRA_SUBJECT, analysis.title)
                                    putExtra(Intent.EXTRA_TEXT, combinedMeta)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        fallback,
                                        if (isBn) "ইউটিউব বা শেয়ার অ্যাপ বেছে নিন" else "Open YouTube or Share"
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("youtube_upload_helper_button")
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBn) "ইউটিউব শেয়ার খুলুন (টেক্সট কপি সহ)" else "Open YouTube Share (Text Copied)")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ChecklistItem(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = label,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSec = durationMs / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
}
