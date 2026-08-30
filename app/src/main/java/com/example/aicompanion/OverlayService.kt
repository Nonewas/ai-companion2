package com.example.aicompanion

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubble: TextView
    private var chatView: View? = null

    private val backendUrl = "https://ai-companion2-jet.vercel.app/api/chat"

    private val bubbleParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 300
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        bubble = TextView(this).apply {
            text = "🤖"
            textSize = 28f
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isDrag = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDrag = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDrag = true
                    bubbleParams.x = initialX + dx
                    bubbleParams.y = initialY + dy
                    windowManager.updateViewLayout(bubble, bubbleParams)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!isDrag) toggleChat()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, bubbleParams)
    }

    private fun toggleChat() {
        if (chatView != null) {
            windowManager.removeView(chatView)
            chatView = null
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
        }

        val responseText = TextView(this).apply {
            text = "Hi! Ask me something."
            setTextColor(Color.BLACK)
        }

        val input = EditText(this).apply {
            hint = "Type a message..."
        }

        val sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener {
                val message = input.text.toString()
                if (message.isNotBlank()) {
                    responseText.text = "Thinking..."
                    sendMessage(message) { reply ->
                        responseText.post { responseText.text = reply }
                    }
                }
            }
        }

        layout.addView(responseText)
        layout.addView(input)
        layout.addView(sendButton)

        val chatParams = WindowManager.LayoutParams(
            700,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x
            y = bubbleParams.y + 100
        }

        windowManager.addView(layout, chatParams)
        chatView = layout
    }

    private fun sendMessage(message: String, callback: (String) -> Unit) {
        Thread {
            try {
                val url = URL(backendUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val body = JSONObject().put("message", message).toString()
                conn.outputStream.use { it.write(body.toByteArray()) }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val reply = JSONObject(response).optString("reply", "No reply")
                callback(reply)
            } catch (e: Exception) {
                callback("Error: ${e.message}")
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bubble.isInitialized) windowManager.removeView(bubble)
        chatView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}