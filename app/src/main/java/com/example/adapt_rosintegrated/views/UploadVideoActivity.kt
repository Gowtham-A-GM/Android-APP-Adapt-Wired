package com.example.adapt_rosintegrated.views

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.adapt_rosintegrated.databinding.ActivityUploadVideoBinding
import com.example.adapt_rosintegrated.model.VideoModel
import com.example.adapt_rosintegrated.viewModel.VideoViewModel
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.adapt_rosintegrated.viewModel.VideoAdapter
import com.example.adapt_rosintegrated.R
import java.io.File

class UploadVideoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUploadVideoBinding
    private lateinit var viewModel: VideoViewModel
    private lateinit var videoAdapter: VideoAdapter
    private var selectedVideoPath: String? = null

    private var isPaused = false
    private var isEditMode = false
    private var editingVideoId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUploadVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[VideoViewModel::class.java]
        setupRecyclerView()
        setupListeners()
        observeVideos()
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter(
            onDeleteClicked = { id -> viewModel.deleteVideoById(id) },
            onEditClicked = { video -> enterEditMode(video) }
        )
        binding.recyclerVideos.apply {
            layoutManager = LinearLayoutManager(this@UploadVideoActivity)
            adapter = videoAdapter
        }
    }

    private fun setupListeners() {
        binding.btnUploadVideo.setOnClickListener { pickVideoFromGallery() }
        binding.btnRegisterVideo.setOnClickListener { registerOrUpdateVideo() }
        binding.btnRefresh.setOnClickListener { refreshRecycler() }
        binding.btnBack.setOnClickListener { finish() }
        binding.tvBack.setOnClickListener { finish() }

        binding.vvVideoPreview.setOnClickListener {
            if (binding.vvVideoPreview.isPlaying) {
                binding.vvVideoPreview.pause()
                binding.btnPlay.setImageResource(R.drawable.ic_pause)
                binding.btnPlay.visibility = View.VISIBLE
                isPaused = true
            } else if (isPaused) {
                binding.btnPlay.visibility = View.GONE
                binding.vvVideoPreview.start()
                isPaused = false
                binding.btnPlay.setImageResource(R.drawable.ic_play)
            }
        }

        binding.vvVideoPreview.setOnCompletionListener {
            binding.btnPlay.setImageResource(R.drawable.ic_play)
            binding.btnPlay.visibility = View.VISIBLE
            isPaused = false
        }

        binding.btnPlay.setOnClickListener {
            binding.btnPlay.visibility = View.GONE
            binding.vvVideoPreview.start()
            isPaused = false
        }
    }

    private fun registerOrUpdateVideo() {
        val videoKey = binding.etVideoKey.text.toString().toIntOrNull()
        val placeName = binding.etPlaceName.text.toString()
        val description = binding.etDescription.text.toString()

        if (videoKey == null || placeName.isBlank() || selectedVideoPath == null) {
            Toast.makeText(this, "Please fill all fields and select a video", Toast.LENGTH_SHORT).show()
            return
        }

        // Check for duplicate videoKey (ignore if updating the same existing record)
        val existingVideo = viewModel.videoList.value?.find {
            it.videoKey == videoKey && it.id != editingVideoId
        }

        if (existingVideo != null) {
            Toast.makeText(this, "Video Key $videoKey already exists. Please use a different key.", Toast.LENGTH_SHORT).show()
            return
        }

        if (isEditMode && editingVideoId != null) {
            val updatedVideo = VideoModel(editingVideoId!!, videoKey, placeName, selectedVideoPath!!, description = description )
            viewModel.updateVideo(updatedVideo)
            Toast.makeText(this, "Video updated", Toast.LENGTH_SHORT).show()
            exitEditMode()
        } else {
            val newVideo = VideoModel(videoKey = videoKey, placeName = placeName, filePath = selectedVideoPath!!, description = description )
            viewModel.insertVideo(newVideo)
            Toast.makeText(this, "Video added", Toast.LENGTH_SHORT).show()
        }
        clearFields()
    }

    private fun enterEditMode(video: VideoModel) {
        isEditMode = true
        editingVideoId = video.id
        binding.etVideoKey.setText(video.videoKey.toString())
        binding.etPlaceName.setText(video.placeName)
        binding.etDescription.setText(video.description)
        selectedVideoPath = video.filePath
        binding.ivVideoPlaceholder.visibility = View.GONE
        binding.vvVideoPreview.visibility = View.VISIBLE
        binding.vvVideoPreview.setVideoURI(Uri.parse(video.filePath))
        binding.btnPlay.visibility = View.VISIBLE
        binding.btnRegisterVideo.text = "SAVE"
    }

    private fun exitEditMode() {
        isEditMode = false
        editingVideoId = null
        binding.btnRegisterVideo.text = "REGISTER"
    }

    private fun clearFields() {
        binding.etVideoKey.text.clear()
        binding.etPlaceName.text.clear()
        binding.etDescription.text.clear()
        selectedVideoPath = null
        binding.vvVideoPreview.visibility = View.GONE
        binding.ivVideoPlaceholder.visibility = View.VISIBLE
        binding.btnPlay.visibility = View.GONE
    }

    private fun refreshRecycler() {
        viewModel.videoList.value?.let { list ->
            val sortedList = list.sortedBy { it.videoKey }
            videoAdapter.submitList(sortedList)
            binding.recyclerVideos.scrollToPosition(0)
        }
    }

    private fun observeVideos() {
        viewModel.videoList.observe(this) { list ->
            val sortedList = list.sortedBy { it.videoKey }
            videoAdapter.submitList(sortedList)
        }
    }

    private fun pickVideoFromGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        galleryLauncher.launch(intent)
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val destFile = File(filesDir, "AdaptRosVideos/video_${System.currentTimeMillis()}.mp4").apply {
                    parentFile?.mkdirs()
                }
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                selectedVideoPath = destFile.absolutePath
                binding.ivVideoPlaceholder.visibility = View.GONE
                binding.vvVideoPreview.visibility = View.VISIBLE
                binding.vvVideoPreview.setVideoURI(Uri.parse(selectedVideoPath))
                binding.btnPlay.visibility = View.VISIBLE
            }
        }
    }
}
