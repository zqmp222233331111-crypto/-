package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.ChatMessage
import com.example.data.model.GalleryMediaItem
import com.example.data.model.MemoryItem
import com.example.data.model.SenderRole

class ShailaConverters {
    @TypeConverter
    fun fromSenderRole(role: SenderRole): String = role.name

    @TypeConverter
    fun toSenderRole(value: String): SenderRole =
        runCatching { SenderRole.valueOf(value) }.getOrDefault(SenderRole.ASSISTANT)
}

@Database(
    entities = [ChatMessage::class, MemoryItem::class, GalleryMediaItem::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ShailaConverters::class)
abstract class ShailaDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao
    abstract fun galleryMediaDao(): GalleryMediaDao

    companion object {
        @Volatile
        private var INSTANCE: ShailaDatabase? = null

        fun getInstance(context: Context): ShailaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShailaDatabase::class.java,
                    "shaila_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
