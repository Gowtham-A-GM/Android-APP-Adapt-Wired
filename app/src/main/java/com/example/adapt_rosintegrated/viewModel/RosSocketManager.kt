package com.example.adapt_rosintegrated.viewModel

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException

class RosSocketManager(
    private val ip: String,
    private val onVideoKeyReceived: (Int) -> Unit, // this is a callback, will pass the videoKey to MainActivity when received
    private val onConnected: () -> Unit
) {

    private lateinit var webSocketClient: WebSocketClient  // our websocket client to talk to ROS
    private var isReconnecting = false
    private var reconnectHandler: Handler? = null
    private var reconnectRunnable: Runnable? = null

    private val advertisedTopics = mutableSetOf<String>()


    fun initRosConnection() {
        Log.d("ROS_DEBUG", "Initializing ROS Connection...")
        connectRosWebSocket()  // start the websocket connection
    }

    private fun stopReconnectAttempts() {
        reconnectRunnable?.let { reconnectHandler?.removeCallbacks(it) }
        reconnectHandler = null
        reconnectRunnable = null
        isReconnecting = false
        Log.d("ROS_DEBUG", "Stopped all reconnect attempts")
    }

    private fun connectRosWebSocket() {
        stopReconnectAttempts()

        if (ip.isBlank()) {
            Log.e("ROS_DEBUG", "Cannot connect: IP is blank!")
            return
        }
        try {
//            val serverUri = URI("ws://172.16.124.45:9090")  // IP of ROS master's
            val serverUri = URI("ws://$ip:9090")
//            val serverUri = URI("ws://rosbot.local:9090")  // IP of ROS master's
            Log.d("ROS_DEBUG", "Attempting to connect to: $serverUri")

            webSocketClient = object : WebSocketClient(serverUri) {

                override fun onOpen(handshake: ServerHandshake?) {
                    Log.d("ROS_DEBUG", "WebSocket Connected Successfully")
                    onConnected()
                    subscribeToRosTopic()  // once connected, subscribe to the required ROS topic
                }

                override fun onMessage(message: String?) {
                    Log.d("ROS_DEBUG", "Received raw message from ROS: $message")
                    message?.let { handleRosSignal(it) }  // whenever message comes from ROS, handle it
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.d("ROS_DEBUG", "WebSocket Closed. Code: $code, Reason: $reason, Remote: $remote")  // connection closed

                    // Attempt reconnect after 5 seconds
                    if (!isReconnecting) {
                        isReconnecting = true
                        reconnectHandler = Handler(Looper.getMainLooper())
                        reconnectRunnable = Runnable {
                            isReconnecting = false
                            Log.d("ROS_DEBUG", "Attempting to reconnect to ROS...")
                            connectRosWebSocket()
                        }
                        reconnectHandler?.postDelayed(reconnectRunnable!!, 5000)

                    }
                }

                override fun onError(ex: Exception?) {
                    Log.e("ROS_DEBUG", "WebSocket Error Occurred: ${ex?.message}")  // some error occurred

                    // Attempt reconnect after 5 seconds
                    if (!isReconnecting) {
                        isReconnecting = true
                        reconnectHandler = Handler(Looper.getMainLooper())
                        reconnectRunnable = Runnable {
                            isReconnecting = false
                            Log.d("ROS_DEBUG", "Attempting to reconnect to ROS...")
                            connectRosWebSocket()
                        }
                        reconnectHandler?.postDelayed(reconnectRunnable!!, 5000)

                    }
                }
            }

            webSocketClient.connect()  // trigger the connection
            Log.d("ROS_DEBUG", "WebSocket connection triggered")

        } catch (e: URISyntaxException) {
            Log.e("ROS_DEBUG", "Invalid URI Exception: ${e.message}")  // this happens if your ws:// URI format is wrong
        }
    }

    private fun subscribeToRosTopic() {
        if (webSocketClient.isOpen) {
            val subscribeMsg = JSONObject().apply {
                put("op", "subscribe")  // standard ROSBridge operation to subscribe
                put("topic", "/video_key")  // name of the topic we are listening to
                put("type", "std_msgs/Int32")  // message type, here it's Int wrapped in ROS std_msgs/Int32
            }
            Log.d("ROS_DEBUG", "Subscribing to topic /video_key with message: $subscribeMsg")
            webSocketClient.send(subscribeMsg.toString())  // send the subscription request to ROS
        } else {
            Log.e("ROS_DEBUG", "WebSocket is not open, cannot subscribe")
        }
    }

    fun advertiseTopic(topic: String) {
        if (::webSocketClient.isInitialized && webSocketClient.isOpen) {
            if (advertisedTopics.contains(topic)) {
                Log.d("ROS_DEBUG", "Topic $topic already advertised, skipping")
                return
            }

            val advertiseMsg = JSONObject().apply {
                put("op", "advertise")
                put("topic", topic)
                put("type", "std_msgs/Int32")
            }

            Log.d("ROS_DEBUG", "Advertising topic $topic")
            webSocketClient.send(advertiseMsg.toString())
            advertisedTopics.add(topic)
        } else {
            Log.e("ROS_DEBUG", "WebSocket is not open, cannot advertise $topic")
        }
    }


    fun publishToRosTopic(topic: String, message: String) {
        if (::webSocketClient.isInitialized && webSocketClient.isOpen) {
            advertiseTopic(topic)

            val publishMsg = JSONObject().apply {
                put("op", "publish")
                put("topic", topic)
                put("msg", JSONObject().apply {
                    put("data", message.toInt())
                })
            }

            Log.d("ROS_DEBUG", "Publishing to $topic -> $message")
            webSocketClient.send(publishMsg.toString())
        } else {
            Log.e("ROS_DEBUG", "WebSocket is not open, cannot publish to $topic")
        }
    }

    private fun handleRosSignal(message: String) {
        try {
            Log.d("ROS_DEBUG", "Handling ROS message: $message")
            val json = JSONObject(message)  // convert entire message string to JSON
            if (json.optString("topic") == "/video_key") {  // check if message is from our expected topic
                val msg = JSONObject(json.optString("msg"))  // extract the "msg" part of the JSON
                val key = msg.getInt("data")   // get the actual integer value sent from ROS

                Log.d("ROS_DEBUG", "Extracted videoKey: $key, passing to callback")

                Handler(Looper.getMainLooper()).post {
                    onVideoKeyReceived(key)
                }
            } else {
                Log.w("ROS_DEBUG", "Received message from unexpected topic: ${json.optString("topic")}")
            }
        } catch (e: Exception) {
            Log.e("ROS_DEBUG", "Error parsing ROS message: ${e.message}")  // in case the JSON is not as expected
        }
    }

    fun disconnect() {
        stopReconnectAttempts()
        if (::webSocketClient.isInitialized && webSocketClient.isOpen) {
            Log.d("ROS_DEBUG", "Closing WebSocket Connection")
            webSocketClient.close()  // close the websocket properly when app is closing
        } else {
            Log.w("ROS_DEBUG", "WebSocket not initialized or already closed")
        }
    }
}