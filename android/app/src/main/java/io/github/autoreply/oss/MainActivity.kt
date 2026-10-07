package io.github.autoreply.oss

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.TimeUnit

@SuppressLint("SetTextI18n")
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F13"))
            setPadding(48, 48, 48, 48)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "AutoReply OSS"
            textSize = 24f
            setTextColor(Color.parseColor("#4CAF50"))
            paint.isFakeBoldText = true
        })

        // Subtitle
        layout.addView(TextView(this).apply {
            text = "Open source AI auto-reply bot"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 10, 0, 40)
        })

        // Notification access button
        layout.addView(Button(this).apply {
            text = "Grant Notification Access"
            background = createRoundedBg("#2196F3")
            setTextColor(Color.WHITE)
            textSize = 14f
            paint.isFakeBoldText = true
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })

        // Battery optimization button
        layout.addView(Button(this).apply {
            text = "Disable Battery Optimization"
            background = createRoundedBg("#FF9800")
            setTextColor(Color.WHITE)
            textSize = 14f
            paint.isFakeBoldText = true
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        })

        // Update check button
        layout.addView(Button(this).apply {
            text = "Check for Updates"
            background = createRoundedBg("#9C27B0")
            setTextColor(Color.WHITE)
            textSize = 14f
            paint.isFakeBoldText = true
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Checking...", Toast.LENGTH_SHORT).show()
                UpdateChecker.check(this@MainActivity, force = true)
            }
        })

        // Info text
        layout.addView(TextView(this).apply {
            text = "\nThe bot will run in the background once notification access is granted."
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, 40, 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(layout) })

        // Auto check update on launch
        UpdateChecker.check(this, force = false)
    }

    private fun createRoundedBg(color: String): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = 50f
        }
    }
}