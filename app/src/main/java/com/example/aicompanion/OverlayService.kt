package com.example.aicompanion

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubble: ImageView
    private var chatView: View? = null

    private val backendUrl = "https://ai-companion2-jet.vercel.app/api/chat"
    private val chatHistory = mutableListOf<Pair<String, String>>()

    private var mediaProjection: MediaProjection? = null

    private val bubbleParams = WindowManager.LayoutParams(
        180,
        180,
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
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val prefs = getSharedPreferences("companion_prefs", MODE_PRIVATE)
        val selectedCharacter = prefs.getString("selected_character", "char1") ?: "char1"

        val imageRes = when (selectedCharacter) {
            "char1" -> R.drawable.char1
            "char2" -> R.drawable.char2
            "char3" -> R.drawable.char3
            else -> R.drawable.char1
        }

        bubble = ImageView(this).apply {
            setImageResource(imageRes)
            layoutParams = android.view.ViewGroup.LayoutParams(150, 150)
        }

        startIdleAnimation()

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("mp_result_code", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("mp_data")
        if (resultCode != -1 && data != null) {
            val projectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "companion_channel"
        val channel = android.app.NotificationChannel(
            channelId,
            "AI Companion",
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = android.app.Notification.Builder(this, channelId)
            .setContentTitle("AI Companion is active")
            .setContentText("Tap the bubble to chat")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }

    private fun startIdleAnimation() {
        val scaleUp = android.animation.ObjectAnimator.ofFloat(bubble, "scaleX", 1f, 1.08f, 1f)
        val scaleUpY = android.animation.ObjectAnimator.ofFloat(bubble, "scaleY", 1f, 1.08f, 1f)
        scaleUp.duration = 1500
        scaleUpY.duration = 1500
        scaleUp.repeatCount = android.animation.ObjectAnimator.INFINITE
        scaleUpY.repeatCount = android.animation.ObjectAnimator.INFINITE
        scaleUp.start()
        scaleUpY.start()
    }

    private fun startTalkingAnimation() {
        val bounce = android.animation.ObjectAnimator.ofFloat(bubble, "rotation", -5f, 5f, -5f)
        bounce.duration = 200
        bounce.repeatCount = android.animation.ObjectAnimator.INFINITE
        bubble.setTag(bounce)
        bounce.start()
    }

    private fun stopTalkingAnimation() {
        (bubble.getTag() as? android.animation.ObjectAnimator)?.cancel()
        bubble.rotation = 0f
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun captureScreen(callback: (String?) -> Unit) {
        val projection = mediaProjection
        if (projection == null) {
            callback(null)
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val virtualDisplay: VirtualDisplay? = projection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    val scaledWidth = 720
                    val scaledHeight = (height * (720f / width)).toInt()
                    val scaled = Bitmap.createScaledBitmap(cropped, scaledWidth, scaledHeight, true)

                    val outputStream = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                    callback(base64)
                } else {
                    callback(null)
                }
            } catch (e: Exception) {
                callback(null)
            } finally {
                virtualDisplay?.release()
                reader.close()
            }
        }, 300)
    }

    private fun toggleChat() {
        if (chatView != null) {
            windowManager.removeView(chatView)
            chatView = null
            return
        }

        val outerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.parseColor("#FFFDF7"), 32f)
            setPadding(28, 20, 28, 28)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = "Companion"
            setTextColor(Color.parseColor("#333333"))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeButton = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#888888"))
            setPadding(16, 8, 16, 8)
            setOnClickListener { toggleChat() }
        }

        topRow.addView(titleText)
        topRow.addView(closeButton)
        outerLayout.addView(topRow)

        val conversationText = TextView(this).apply {
            text = "Hi! Ask me something."
            setTextColor(Color.parseColor("#333333"))
            textSize = 14f
            setPadding(8, 20, 8, 20)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 260)
            addView(conversationText)
        }
        outerLayout.addView(scrollView)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 0)
        }

        val input = EditText(this).apply {
            hint = "Type a message..."
            background = roundedBackground(Color.parseColor("#F0F0F0"), 24f)
            setPadding(24, 16, 24, 16)
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sendButton = TextView(this).apply {
            text = "Send"
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.parseColor("#5B8DEF"), 24f)
            setPadding(28, 16, 28, 16)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginStart = 12
            layoutParams = lp

            setOnClickListener {
                val message = input.text.toString()
                if (message.isNotBlank()) {
                    input.setText("")
                    conversationText.append("\n\nYou: $message")
                    conversationText.append("\n\nThinking...")
                    startTalkingAnimation()

                    val needsScreen = message.contains("screen", true) || message.contains("this", true)

                    fun finish(reply: String) {
                        conversationText.post {
                            val current = conversationText.text.toString().removeSuffix("\n\nThinking...")
                            conversationText.text = current
                            conversationText.append("\n\nCompanion: $reply")
                            chatHistory.add("user" to message)
                            chatHistory.add("model" to reply)
                            stopTalkingAnimation()
                        }
                    }

                    if (needsScreen) {
                        captureScreen { base64 ->
                            sendMessage(message, base64) { reply -> finish(reply) }
                        }
                    } else {
                        sendMessage(message, null) { reply -> finish(reply) }
                    }
                }
            }
        }

        inputRow.addView(input)
        inputRow.addView(sendButton)
        outerLayout.addView(inputRow)

        val chatParams = WindowManager.LayoutParams(
            720,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x
            y = bubbleParams.y + 100
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }

        windowManager.addView(outerLayout, chatParams)
        chatView = outerLayout
    }

    private fun sendMessage(message: String, image: String?, callback: (String) -> Unit) {
        Thread {
            try {
                val url = URL(backendUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val historyArray = JSONArray()
                chatHistory.forEach { (role, text) ->
                    val obj = JSONObject()
                    obj.put("role", role)
                    obj.put("text", text)
                    historyArray.put(obj)
                }

                val body = JSONObject()
                body.put("message", message)
                body.put("history", historyArray)
                if (image != null) body.put("image", image)

                conn.outputStream.use { it.write(body.toString().toByteArray()) }

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
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}