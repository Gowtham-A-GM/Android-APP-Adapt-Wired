package com.example.adapt_wired.viewModel


import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.lang.System.out
import java.net.Socket

class WiredSocketManager(
    private val host: String = "127.0.0.1",   // forwarded via adb reverse
    private val port: Int = 5000,
    private val onVideoKeyReceived: (Int) -> Unit,
    private val onConnected: () -> Unit
) {
    private val TAG = "WIRED_DEBUG"

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initConnection() {
        scope.cancel()  // cancel old scope before creating new
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                Log.d(TAG, "Connecting to $host:$port ...")
                socket = Socket(host, port)
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                writer = PrintWriter(socket!!.getOutputStream(), true)

                Log.d(TAG, "Connected successfully to Python server")
                withContext(Dispatchers.Main) { onConnected() }

                listenForMessages()
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}", e)
                delay(5000)
                initConnection() // retry after 5s
            }
        }
    }

    private suspend fun listenForMessages() {
        try {
            var line: String?
            while (reader?.readLine().also { line = it } != null) {
                Log.d(TAG, "Received -> $line")
                line?.let { handleMessage(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in listen loop: ${e.message}", e)
            delay(5000)
            initConnection() // try reconnect
        }
    }

    private fun handleMessage(message: String) {
        val key = message.trim().toIntOrNull()
        if (key != null) {
            Log.d(TAG, "Received videoKey: $key, passing to callback")
            Handler(Looper.getMainLooper()).post {
                onVideoKeyReceived(key)
            }
        } else {
            Log.w(TAG, "Invalid message (not an int): $message")
        }
    }


    fun publishMessage(message: String) {
        Thread {
            try {
                writer?.println(message)
                writer?.flush()
                Log.d("WIRED_DEBUG", "Message sent: $message")
            } catch (e: Exception) {
                Log.e("WIRED_DEBUG", "Failed to publish: ${e.message}", e)
            }
        }.start()
    }




    fun disconnect() {
        scope.cancel()
        try {
            socket?.close()
            Log.d(TAG, "Socket closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}", e)
        }
    }
}
