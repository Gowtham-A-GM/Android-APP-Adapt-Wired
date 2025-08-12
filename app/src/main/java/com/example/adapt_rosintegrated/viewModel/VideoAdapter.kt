package com.example.adapt_rosintegrated.viewModel

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.example.adapt_rosintegrated.R
import com.example.adapt_rosintegrated.model.VideoModel

class VideoAdapter(
    private val onDeleteClicked: (Int) -> Unit,
    private val onEditClicked: (VideoModel) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private var videoList: List<VideoModel> = emptyList()

    fun submitList(list: List<VideoModel>) {
        videoList = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_uploadvideo, parent, false)
        return VideoViewHolder(view)
    }

    override fun getItemCount(): Int = videoList.size

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videoList[position]
        holder.bind(video)
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val videoView: VideoView = itemView.findViewById(R.id.itemVideoView)
        private val playButton: ImageButton = itemView.findViewById(R.id.itemPlayBtn)
        private val placeName: TextView = itemView.findViewById(R.id.tv_placeName)
        private val videoKey: TextView = itemView.findViewById(R.id.tv_videoKey)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btn_edit)

        private var isPaused = true

        fun bind(video: VideoModel) {

            videoView.setVideoPath(video.filePath)
            playButton.setImageResource(R.drawable.ic_play_item)
            playButton.visibility = View.VISIBLE
            isPaused = true

            placeName.text = video.placeName
            videoKey.text = "Video Key: ${video.videoKey}"

            btnDelete.setOnClickListener {
                onDeleteClicked(video.id)
            }

            btnEdit.setOnClickListener {
                onEditClicked(video)
            }

            videoView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.setVolume(0f, 0f)  // Mute the video
            }

            playButton.setOnClickListener {
                playButton.visibility = View.GONE
                videoView.start()
                isPaused = false
                playButton.setImageResource(R.drawable.ic_play_item)
            }

            videoView.setOnClickListener {
                if (videoView.isPlaying) {
                    videoView.pause()
                    playButton.setImageResource(R.drawable.ic_pause_item)
                    playButton.visibility = View.VISIBLE
                    isPaused = true
                } else if (isPaused) {
                    playButton.visibility = View.GONE
                    videoView.start()
                    isPaused = false
                    playButton.setImageResource(R.drawable.ic_play_item)
                }
            }

            videoView.setOnCompletionListener {
                playButton.setImageResource(R.drawable.ic_play_item)
                playButton.visibility = View.VISIBLE
                isPaused = true
            }
        }
    }
}
