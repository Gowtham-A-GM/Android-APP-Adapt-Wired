package com.example.adapt_wired.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [VideoModel::class], version = 2, exportSchema = false)
abstract class AdaptRosDB : RoomDatabase() {

    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile
        private var INSTANCE: AdaptRosDB? = null

        // ✅ Define migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE videos ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AdaptRosDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AdaptRosDB::class.java,
                    "video_database"
                )
                    .addMigrations(MIGRATION_1_2) // ✅ Safe migration added here
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
