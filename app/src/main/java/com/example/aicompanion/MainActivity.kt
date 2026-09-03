package com.example.aicompanion

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.view.Gravity
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    private val characters = listOf(
        "char1" to R.drawable.char1,
        "char2" to R.drawable.char2,
        "char3" to R.drawable.char3
    )

    private lateinit var prefs: SharedPreferences
    private var selectedCharacter = "char1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("companion_prefs", MODE_PRIVATE)
        selectedCharacter = prefs.getString("selected_character", "char1") ?: "char1"

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 100, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Choose your character"
            textSize = 18f
            setPadding(0, 0, 0, 30)
        }
        mainLayout.addView(title)

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val imageViews = mutableListOf<Pair<String, ImageView>>()

        characters.forEach { (name, resId) ->
            val img = ImageView(this).apply {
                setImageResource(resId)
                layoutParams = LinearLayout.LayoutParams(150, 150).apply {
                    setMargins(10, 10, 10, 10)
                }
                setBackgroundColor(
                    if (name == selectedCharacter) Color.parseColor("#CCE5FF") else Color.TRANSPARENT
                )
                setOnClickListener {
                    selectedCharacter = name
                    prefs.edit().putString("selected_character", name).apply()
                    imageViews.forEach { (n, iv) ->
                        iv.setBackgroundColor(
                            if (n == name) Color.parseColor("#CCE5FF") else Color.TRANSPARENT
                        )
                    }
                }
            }
            imageViews.add(name to img)
            grid.addView(img)
        }

        mainLayout.addView(grid)

        val enableButton = Button(this).apply {
            text = "Enable Floating Character"
            setPadding(0, 60, 0, 0)
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    startService(Intent(this@MainActivity, OverlayService::class.java))
                }
            }
        }

        mainLayout.addView(enableButton)
        setContentView(mainLayout)
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, OverlayService::class.java))
        }
    }
}