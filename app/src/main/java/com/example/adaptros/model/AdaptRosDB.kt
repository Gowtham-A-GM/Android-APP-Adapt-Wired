package com.example.adaptros.model

import com.example.adaptros.db.VideoDao
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VideoModel::class], version = 1)
abstract class AdaptRosDB : RoomDatabase() {

    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile private var INSTANCE: AdaptRosDB? = null

        fun getDatabase(context: Context): AdaptRosDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AdaptRosDB::class.java,
                    "video_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
