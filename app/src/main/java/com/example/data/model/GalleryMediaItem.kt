package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gallery_media")
data class GalleryMediaItem(
    @PrimaryKey val uri: String,
    val isVideo: Boolean,
    val displayName: String,
    val durationMs: Long = 0L,
    val addedAt: Long = System.currentTimeMillis()
)

data class VideoAnalysisResult(
    val title: String,
    val description: String,
    val tags: String,
    val caption: String,
    val rawSummary: String
)
