package com.example.adaptros.views

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.adaptros.databinding.ActivityMainBinding
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.adaptros.MyDeviceAdminReceiver
import com.example.adaptros.R

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // for locking app
    private var tapCount = 0
    private var lastTapTime = 0L

    // for secret btn
    private var showState = 0
    private var hideState = 0

    // for displaying corresponding video from dictionary
    private val videoMap = hashMapOf(
        1 to R.raw.bg_video_speaking
    )

    var cnt = 0

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // locking the screen, only possible to get out on tapping 10 times
//        lockSystemUI()
        setupSecretExitTap()
        setupSecretBtn()
        playBGVideo(-1)   // playing background video (in LOOP)
        rosInit()
        uploadVideoBtnInit()

    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun lockSystemUI(){
        val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // If already a device owner or admin:
        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            devicePolicyManager.setLockTaskPackages(componentName, arrayOf(packageName))
            startLockTask()
        } else {
            // Fallback: allow lock task anyway (if app is manually whitelisted)
            startLockTask()
        }

        window.decorView.windowInsetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupSecretExitTap() {
        val tapZone = findViewById<View>(R.id.v_secretTopLeft)
        tapZone.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 800) {
                tapCount++
            } else {
                tapCount = 1
            }
            lastTapTime = currentTime

            if (tapCount >= 10) {
                tapCount = 0
                stopLockTask()
                finish() // Optional: or navigate to another screen
                Toast.makeText(this, "Exiting lock mode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSecretBtn() {

        binding.vSecretBottomRight.setOnClickListener {
            // Show Pattern
            if (showState in 0..2) {
                showState++
            } else {
                showState = 0
            }

            // Hide Pattern
            if (hideState in 0..1) {
                hideState++
            } else {
                hideState = 0
            }
        }

        binding.vSecretBottomLeft.setOnClickListener {
            // Show Pattern
            if (showState in 3..5) {
                showState++
                if (showState == 6) {
                    binding.btnUploadVideo.visibility = View.VISIBLE
                    showState = 0
                }
            } else {
                showState = 0
            }

            // Hide Pattern
            if (hideState in 2..3) {
                hideState++
                if (hideState == 4) {
                    binding.btnUploadVideo.visibility = View.GONE
                    hideState = 0
                }
            } else {
                hideState = 0
            }
        }
    }

    // for playing background video (in LOOP)
    private fun playBGVideo(value: Int) {
        val videoResId = videoMap[value] ?: R.raw.bg_video_ideal  // Default video if unknown

        val videoUri = Uri.parse("android.resource://$packageName/$videoResId")
        binding.vvBackgroundVideo.setVideoURI(videoUri)
        binding.vvBackgroundVideo.setOnPreparedListener {
            it.isLooping = true
            it.setVolume(0f, 0f)
            binding.vvBackgroundVideo.start()
        }
    }

    private fun rosInit(){

        binding.btnRosTest.setOnClickListener {
            if(cnt==0){
                playBGVideo(1)
                cnt = 1
            }
            else {
                playBGVideo(-1)
                cnt = 0
            }
        }


    }

    private fun uploadVideoBtnInit(){
        binding.btnUploadVideo.setOnClickListener {
            val intent = Intent(this, UploadVideoActivity::class.java)
            startActivity(intent)
        }
    }
}