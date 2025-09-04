package com.example.adapt_wired.views

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.adapt_wired.R
import com.example.adapt_wired.databinding.ActivityMainBinding
import com.example.adapt_wired.viewModel.VideoViewModel
import java.util.Locale
import android.speech.tts.UtteranceProgressListener
import com.example.adapt_wired.viewModel.WiredSocketManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var videoViewModel: VideoViewModel
    private lateinit var wiredSocketManager: WiredSocketManager

    private var tapCount = 0
    private var lastTapTime = 0L
    private var secretBtnState = 0

    private lateinit var tts: TextToSpeech
    private var isSpeaking = false

    private val videoDescriptionMap = hashMapOf<Int, String>()

    private val videoPathMap = hashMapOf<Int, String>()  // stores videoKey -> filePath mapping for quick lookup
    private lateinit var speakingVideoUri: Uri
    private lateinit var idealVideoUri: Uri


//    private var rosIp = "127.0.0.1"  // Default IP, can change dynamically - 172.16.124.45
    private var lastPlayedKey: Int? = null
    private var connectingToast: Toast? = null

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoViewModel = ViewModelProvider(this).get(VideoViewModel::class.java)  // get ViewModel instance
        setupVideoObserver()  // observe DB for video list and update map

        speakingVideoUri = Uri.parse("android.resource://$packageName/${R.raw.bg_video_speaking}")
        idealVideoUri = Uri.parse("android.resource://$packageName/${R.raw.bg_video_ideal}")


        initTTS()
        lockSystemUI()  // optional: lock system UI for kiosk mode
        lockApp()
        setupSecretExitTap()  // hidden tap area to exit kiosk
        setupSecretBtn()  // hidden pattern to show/hide upload button
        playBGVideo(null)  // play default background video
        connectToWiredServer()
        uploadVideoBtnInit()  // setup upload button click listener

    }

    private fun initTTS(){
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread {
                            isSpeaking = true
                            playBGVideo("android.resource://$packageName/${R.raw.bg_video_speaking}")
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            isSpeaking = false
                            // Clear text and go back to default background
                            binding.tvDescription.animate()
                                .alpha(0f)
                                .setDuration(600)
                                .withEndAction {
                                    binding.tvDescription.text = ""
                                    binding.tvDescription.alpha = 1f
                                    playBGVideo(null)
                                }
                                .start()
                        }
                    }


                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
//                            binding.tvDescription.text = ""
                            binding.tvDescription.animate()
                                .alpha(0f)
                                .setDuration(600)
                                .withEndAction {
                                    binding.tvDescription.text = ""
                                    binding.tvDescription.alpha = 1f  // Reset for next use
                                    playBGVideo(null)
                                }
                                .start()

                        }
                    }
                })

            }
        }
    }

    private fun connectToWiredServer() {
        if (::wiredSocketManager.isInitialized) {
            wiredSocketManager.disconnect()
        }
        wiredSocketManager = WiredSocketManager(
            "127.0.0.1",
            5000,  // TCP port your Python server listens on
            onVideoKeyReceived = { videoKey ->
                handleWiredVideoKey(videoKey)   // reuse same function
                Log.d("Main", "Video key received in MainActivity: $videoKey")
            },
            onConnected = {
                runOnUiThread {
                    connectingToast?.cancel()
                    Toast.makeText(this, "Connected to TBot", Toast.LENGTH_SHORT).show()
                }
            }
        )
        wiredSocketManager.initConnection()
    }


    private fun handleWiredVideoKey(videoKey: Int) {
        if (lastPlayedKey == videoKey) return
        Log.d("Main", "HandleRosVideoKey: $videoKey")
        lastPlayedKey = videoKey

        val path = videoPathMap[videoKey]
        val desc = videoDescriptionMap[videoKey]

        if (desc != null) {
            binding.tvDescription.apply {
                alpha = 0f
                text = desc.trim()
                animate().alpha(1f).setDuration(400).start()
            }

            val params = Bundle()
            val utteranceId = "desc_$videoKey"

            // Store path to play *after* TTS completes
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        playBGVideo("android.resource://$packageName/${R.raw.bg_video_speaking}")
                    }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        binding.tvDescription.animate()
                            .alpha(0f)
                            .setDuration(600)
                            .withEndAction {
                                binding.tvDescription.text = ""
                                binding.tvDescription.alpha = 1f
                                if (path != null) {
                                    playBGVideo(path)
                                } else {
                                    playBGVideo(null)
                                    Toast.makeText(this@MainActivity, "No video mapped for key: $videoKey", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .start()
                    }
                }

                override fun onError(utteranceId: String?) {
                    onDone(utteranceId) // treat error as done
                }
            })

            tts.speak(desc, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            // No description, play video immediately
            if (path != null) {
                playBGVideo(path)
            } else {
                playBGVideo(null)
                Toast.makeText(this, "No video mapped for key: $videoKey", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun lockSystemUI() {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun lockApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLockTask()
        }
    }

    private fun setupSecretExitTap() {
        val tapZone = findViewById<View>(R.id.v_secretTopLeft)
        tapZone.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            tapCount = if (currentTime - lastTapTime < 800) tapCount + 1 else 1  // detect rapid taps
            lastTapTime = currentTime

            if (tapCount >= 10) {
                tapCount = 0
                stopLockTask()
                finish()
                Toast.makeText(this, "Exiting lock mode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSecretBtn() {
        binding.vSecretBottomRight.setOnClickListener {
            secretBtnState = if (secretBtnState in 0..1) secretBtnState + 1 else 0
        }
        binding.vSecretBottomLeft.setOnClickListener {
            if (secretBtnState in 2..3) secretBtnState++ else secretBtnState = 0

            if (secretBtnState == 4) {
                binding.btnUploadVideo.visibility =
                    if (binding.btnUploadVideo.visibility == View.GONE) View.VISIBLE else View.GONE
                secretBtnState = 0
            }
        }
    }

    private fun playBGVideo(videoPath: String?) {
        val videoUri = when {
            videoPath.isNullOrEmpty() -> idealVideoUri
            videoPath.startsWith("android.resource://") -> Uri.parse(videoPath)
            else -> Uri.parse(videoPath)
        }

        Log.d("VIDEO", "Setting up video: $videoUri")

        binding.vvBackgroundVideo.setVideoURI(videoUri)

        binding.vvBackgroundVideo.setOnPreparedListener {
            val isLoop = (videoUri == idealVideoUri || videoUri == speakingVideoUri)
            it.isLooping = isLoop
            Log.d("VIDEO", "Prepared: $videoUri | Looping: $isLoop")
            binding.vvBackgroundVideo.start()
        }

        binding.vvBackgroundVideo.setOnCompletionListener {
            val uriStr = videoUri.toString()
            val speakingUriStr = speakingVideoUri.toString()
            val idealUriStr = idealVideoUri.toString()

            Log.d("VIDEO", "Completed: $uriStr | isSpeaking: $isSpeaking")

            if (uriStr == speakingUriStr && isSpeaking) {
                Log.d("VIDEO", "TTS still speaking... replaying speaking video.")
                playBGVideo(uriStr)  // replay
            } else if (uriStr != idealUriStr && uriStr != speakingUriStr) {
                Log.d("ROS_PLAY", "Custom video finished. Sending startmove = 01")
                wiredSocketManager.publishMessage("1")
                playBGVideo(null)
            } else {
                Log.d("VIDEO", "Video done. Switching to ideal.")
                playBGVideo(null)
            }
        }

    }


    private fun uploadVideoBtnInit() {
        binding.btnUploadVideo.setOnClickListener {
            val intent = Intent(this, UploadVideoActivity::class.java)  // open video upload screen
            startActivity(intent)
        }
    }

    private fun setupVideoObserver() {
        videoViewModel.videoList.observe(this) { list ->
            videoPathMap.clear()
            list.forEach { video ->
                videoPathMap[video.videoKey] = video.filePath  // populate map for quick video lookup using key
                videoDescriptionMap[video.videoKey] = video.description
            }
        }
    }

    override fun onResume() {
        super.onResume()
        videoViewModel.loadAllVideos()  // reload videos when coming back to this screen
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wiredSocketManager.isInitialized) {
            wiredSocketManager.disconnect()
        }
    }

}