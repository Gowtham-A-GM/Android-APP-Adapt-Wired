package com.example.adapt_rosintegrated.views

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.adapt_rosintegrated.MyDeviceAdminReceiver
import com.example.adapt_rosintegrated.R
import com.example.adapt_rosintegrated.databinding.ActivityMainBinding
import com.example.adapt_rosintegrated.viewModel.RosSocketManager
import com.example.adapt_rosintegrated.viewModel.VideoViewModel
import java.util.Locale
import android.speech.tts.UtteranceProgressListener


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var videoViewModel: VideoViewModel
    private lateinit var rosSocketManager: RosSocketManager  // manager to handle ROS connection and videoKeys

    private var tapCount = 0
    private var lastTapTime = 0L
    private var secretBtnState = 0

    private lateinit var tts: TextToSpeech
    private var isSpeaking = false

    private val videoDescriptionMap = hashMapOf<Int, String>()

    private val videoPathMap = hashMapOf<Int, String>()  // stores videoKey -> filePath mapping for quick lookup
    private lateinit var speakingVideoUri: Uri
    private lateinit var idealVideoUri: Uri


    private var rosIp = ""  // Default IP, can change dynamically - 172.16.124.45
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

        // Save it
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit().putString("ros_ip", rosIp).apply()

        // Load it
        rosIp = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("ros_ip", "") ?: ""


//        initBatteryOptimization()
//        initWifiForce()

        initTTS()
        lockSystemUI()  // optional: lock system UI for kiosk mode
        lockApp()
        setupSecretExitTap()  // hidden tap area to exit kiosk
        setupSecretBtn()  // hidden pattern to show/hide upload button
        playBGVideo(null)  // play default background video
        setupRosIpButton()  // initialize ROS connection
        if (rosIp.isNotBlank()) {
            connectToRos()
        }
        uploadVideoBtnInit()  // setup upload button click listener

    }

    private fun initBatteryOptimization(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

    }

    private fun initWifiForce(){
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
        wifiManager.isWifiEnabled = true  // Turn on WiFi (no-op if already on)
        AlertDialog.Builder(this)
            .setTitle("Disable Auto Mobile Data Switch")
            .setMessage("To avoid WiFi disconnection, please disable 'Switch to mobile data automatically' in Developer Options.")
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun setupRosIpButton() {
        binding.btnSettings.setOnClickListener {
            val editText = EditText(this)
            editText.hint = "Enter ROS IP (e.g. 192.168.0.105)"
            editText.setText(rosIp)  // Pre-fill with current IP

            AlertDialog.Builder(this)
                .setTitle("Set ROS IP Address")
                .setView(editText)
                .setPositiveButton("Connect") { _, _ ->
                    rosIp = editText.text.toString().trim()
                    if (rosIp.isNotEmpty()) {
                        Toast.makeText(this, "Connecting to $rosIp", Toast.LENGTH_SHORT).show()
                        connectToRos()
                    } else {
                        Toast.makeText(this, "Invalid IP", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
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

    private fun connectToRos() {
        if (::rosSocketManager.isInitialized) {
            rosSocketManager.disconnect()
        }
        rosSocketManager = RosSocketManager(
            rosIp,
            onVideoKeyReceived = { videoKey ->
                handleRosVideoKey(videoKey)
            },
            onConnected = {
                runOnUiThread {
                    connectingToast?.cancel()
                    Toast.makeText(this, "Connected to $rosIp", Toast.LENGTH_SHORT).show()
                }
            }
        )
        rosSocketManager.initRosConnection()
    }

    private fun handleRosVideoKey(videoKey: Int) {
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
                binding.btnSettings.visibility =
                    if (binding.btnSettings.visibility == View.GONE) View.VISIBLE else View.GONE
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

//        binding.vvBackgroundVideo.setOnCompletionListener {
//            Log.d("VIDEO", "Completed: $videoUri | isSpeaking: $isSpeaking")
//
//            if (videoUri == speakingVideoUri && isSpeaking) {
//                Log.d("VIDEO", "TTS still speaking... replaying speaking video.")
//                playBGVideo(videoUri.toString())  // replay
//            } else if (videoUri != idealVideoUri && videoUri != speakingVideoUri) {
//                Log.d("ROS_PLAY", "Custom video finished. Sending startmove = 01")
//                rosSocketManager.publishToRosTopic("/start_movement", "01")
//                playBGVideo(null)
//            } else {
//                Log.d("VIDEO", "Video done. Switching to ideal.")
//                playBGVideo(null)
//            }
//        }

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
                rosSocketManager.publishToRosTopic("/start_movement", "01")
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
        if (::rosSocketManager.isInitialized) {
            rosSocketManager.disconnect()
        }
    }
}