package com.example.adaptros.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.adaptros.model.AdaptRosDB
import com.example.adaptros.model.VideoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AdaptRosDB.getDatabase(application).videoDao()

    private val _videoList = MutableLiveData<List<VideoModel>>()
    val videoList: LiveData<List<VideoModel>> = _videoList

    init {
        loadAllVideos()
    }

    fun loadAllVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            val videos = dao.getAllVideos()
            _videoList.postValue(videos)
        }
    }

    fun insertVideo(video: VideoModel) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(video)
            loadAllVideos()
        }
    }

    fun updateVideo(video: VideoModel) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateVideo(video.id, video.videoKey, video.placeName, video.filePath)
            loadAllVideos()
        }
    }

    fun deleteVideoById(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteById(id)
            loadAllVideos()
        }
    }
}
