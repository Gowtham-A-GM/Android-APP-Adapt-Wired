package com.example.adaptros.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoModel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val videoKey: Int,
    val placeName: String,
    val filePath: String
)