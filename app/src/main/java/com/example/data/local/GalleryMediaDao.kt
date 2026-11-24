package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GalleryMediaItem
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryMediaDao {
    @Query("SELECT * FROM gallery_media ORDER BY addedAt DESC")
    fun observeMedia(): Flow<List<GalleryMediaItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GalleryMediaItem>)

    @Query("DELETE FROM gallery_media WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM gallery_media")
    suspend fun clearAll()
}
