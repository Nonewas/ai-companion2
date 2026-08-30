package com.example.aicompanion

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubble: TextView

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        bubble = TextView(this).apply {
            text = "🤖"
            textSize = 28f
        }

        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(bubble, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bubble.isInitialized) windowManager.removeView(bubble)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}