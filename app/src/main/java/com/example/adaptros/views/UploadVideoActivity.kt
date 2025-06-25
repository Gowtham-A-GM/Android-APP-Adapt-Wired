package com.example.adaptros.views

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.adaptros.databinding.ActivityUploadVideoBinding
import com.example.adaptros.model.VideoModel
import com.example.adaptros.viewModel.VideoViewModel
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.adaptros.viewModel.VideoAdapter
import com.example.adaptros.R
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

        viewModel = ViewModelProvider(this).get(VideoViewModel::class.java)

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

        binding.btnUploadVideo.setOnClickListener {
            pickVideoFromGallery()
        }

        binding.btnPlay.setOnClickListener {
            binding.btnPlay.visibility = View.GONE
            binding.vvVideoPreview.start()
            isPaused = false
            binding.btnPlay.setImageResource(R.drawable.ic_play)
        }

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

        binding.btnRegisterVideo.setOnClickListener {
            val videoKey = binding.etVideoKey.text.toString().toIntOrNull()
            val placeName = binding.etPlaceName.text.toString()

            if (videoKey == null || placeName.isBlank() || selectedVideoPath == null) {
                Toast.makeText(this, "Please fill all fields and select a video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isEditMode && editingVideoId != null) {
                val updatedVideo = VideoModel(id = editingVideoId!!, videoKey = videoKey, placeName = placeName, filePath = selectedVideoPath!!)
                viewModel.updateVideo(updatedVideo)
                Toast.makeText(this, "Video updated", Toast.LENGTH_SHORT).show()
                exitEditMode()
            } else {
                val newVideo = VideoModel(videoKey = videoKey, placeName = placeName, filePath = selectedVideoPath!!)
                viewModel.insertVideo(newVideo)
                Toast.makeText(this, "Video added", Toast.LENGTH_SHORT).show()
            }

            clearFields()
        }

        binding.btnRefresh.setOnClickListener {
            refreshRecycler()
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.tvBack.setOnClickListener { finish() }
    }

    private fun enterEditMode(video: VideoModel) {
        isEditMode = true
        editingVideoId = video.id

        binding.etVideoKey.setText(video.videoKey.toString())
        binding.etPlaceName.setText(video.placeName)
        selectedVideoPath = video.filePath

        binding.ivVideoPlaceholder.visibility = View.GONE
        binding.vvVideoPreview.visibility = View.VISIBLE
        binding.btnPlay.visibility = View.VISIBLE

        binding.vvVideoPreview.setVideoURI(Uri.parse(video.filePath))

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
        selectedVideoPath = null
        binding.vvVideoPreview.visibility = View.GONE
        binding.ivVideoPlaceholder.visibility = View.VISIBLE
        binding.btnPlay.visibility = View.GONE
        binding.ivVideoPlaceholder.setImageResource(R.drawable.ic_video_placeholder_uploadvideo)
        binding.btnPlay.setImageResource(R.drawable.ic_play)
        isPaused = false
    }

    private fun refreshRecycler() {
        viewModel.videoList.value?.let {
            videoAdapter.submitList(it)
        }
        binding.recyclerVideos.scrollToPosition(0)
    }

    private fun observeVideos() {
        viewModel.videoList.observe(this) { list ->
            videoAdapter.submitList(list)
        }
    }

    private fun pickVideoFromGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "video/*"
        galleryLauncher.launch(intent)
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val videoUri: Uri? = result.data?.data
            videoUri?.let {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val inputStream = contentResolver.openInputStream(it)
                val appVideosDir = File(filesDir, "AdaptRosVideos")
                if (!appVideosDir.exists()) appVideosDir.mkdirs()

                val destFile = File(appVideosDir, "video_${System.currentTimeMillis()}.mp4")

                inputStream?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                selectedVideoPath = destFile.absolutePath

                binding.ivVideoPlaceholder.visibility = View.GONE
                binding.vvVideoPreview.visibility = View.VISIBLE
                binding.btnPlay.visibility = View.VISIBLE

                binding.vvVideoPreview.setVideoURI(it)
            }
        }
    }
}
