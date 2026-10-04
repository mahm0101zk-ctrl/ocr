package com.mmm.bubbleocr

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            gravity = Gravity.TOP
        }
        root.addView(TextView(this).apply {
            text = "Bubble OCR"
            textSize = 28f
            setTextColor(Color.BLACK)
        })
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, pad)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(TextView(this).apply {
            textSize = 14f
            setPadding(0, pad, 0, 0)
            text = "1. Open Accessibility settings, then Installed apps, then Bubble OCR, then turn it ON.\n\n" +
                "2. If the switch is greyed out (Android 13+): open Settings > Apps > Bubble OCR > menu (3 dots) > " +
                "\"Allow restricted settings\", then try again.\n\n" +
                "3. A red bubble appears over every app. Tap it, drag on the frozen screen to select the text, " +
                "adjust with the handles, press \"Read text\", then Copy."
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val enabled = isServiceEnabled()
        status.text = if (enabled) "Status: ON. The bubble is active." else "Status: OFF. Enable the service below."
        status.setTextColor(if (enabled) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
    }

    private fun isServiceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.split(':').any { it.startsWith("$packageName/") && it.contains("OcrService") }
    }
}
