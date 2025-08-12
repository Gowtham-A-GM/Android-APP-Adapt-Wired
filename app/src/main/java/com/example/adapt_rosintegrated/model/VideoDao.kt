package com.example.adapt_rosintegrated.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface VideoDao {

    @Insert
    suspend fun insert(video: VideoModel)

    @Query("UPDATE videos SET videoKey = :videoKey, placeName = :placeName, filePath = :filePath, description = :description WHERE id = :id")
    suspend fun updateVideo(id: Int, videoKey: Int, placeName: String, filePath: String, description: String)

    @Query("SELECT * FROM videos")
    suspend fun getAllVideos(): List<VideoModel>

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteById(id: Int)
}