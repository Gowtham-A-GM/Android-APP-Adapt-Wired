package com.example.adaptros.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.adaptros.model.VideoModel

@Dao
interface VideoDao {

    @Insert
    suspend fun insert(video: VideoModel)

    @Query("UPDATE videos SET videoKey = :videoKey, placeName = :placeName, filePath = :filePath WHERE id = :id")
    suspend fun updateVideo(id: Int, videoKey: Int, placeName: String, filePath: String)


    @Query("SELECT * FROM videos")
    suspend fun getAllVideos(): List<VideoModel>

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteById(id: Int)
}