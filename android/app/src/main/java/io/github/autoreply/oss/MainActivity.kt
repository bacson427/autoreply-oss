package io.github.autoreply.oss

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object AppLog {
    private val _logs = MutableSharedFlow<String>(replay = 100)
    val logs = _logs.asSharedFlow()
    
    fun add(context: Context, icon: String, title: String, message: String) {
        val now = java.util.Date()
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(now)
        val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(now)

        val prefs = context.getSharedPreferences("AutoReplyLogs", Context.MODE_PRIVATE)
        val lastDate = prefs.getString("last_log_date", "") ?: ""

        var logEntry = ""
        if (lastDate != dateStr) {
            logEntry += "--- 📅 Date $dateStr ---\n"
            prefs.edit().putString("last_log_date", dateStr).apply()
        }

        logEntry += "$icon [$timeStr] $title:\n$message"

        _logs.tryEmit(logEntry)

        val currentLogs = prefs.getString("logs", "") ?: ""
        var newLogs = currentLogs + logEntry + "\n\n"
        if (newLogs.length > 30000) {
            newLogs = newLogs.substring(newLogs.length - 30000)
        }
        prefs.edit().putString("logs", newLogs).apply()
    }

    fun load(context: Context): String {
        return context.getSharedPreferences("AutoReplyLogs", Context.MODE_PRIVATE).getString("logs", "") ?: ""
    }

    fun clear(context: Context) {
        context.getSharedPreferences("AutoReplyLogs", Context.MODE_PRIVATE)
            .edit().remove("logs").remove("last_log_date").apply()
        _logs.tryEmit("__CLEARED__")
    }
}

@SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode")
class MainActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var prefs: SharedPreferences
    
    // UI Elements
    private lateinit var switchMaster: Switch
    private lateinit var switchAI: Switch
    private lateinit var switchManualBusy: Switch
    private lateinit var cardMaster: LinearLayout
    private lateinit var cardAI: LinearLayout
    private lateinit var cardManual: LinearLayout

    // Server & Config Inputs
    private lateinit var editServerUrl: EditText
    private lateinit var editBotSecret: EditText
    private lateinit var editMyNames: EditText
    private lateinit var editBusyMsg: EditText
    private lateinit var editPrompt: EditText
    private lateinit var editYesName: EditText
    private lateinit var editNoName: EditText

    private lateinit var rangesContainer: LinearLayout
    private lateinit var appsContainer: LinearLayout
    private lateinit var btnRow: LinearLayout
    private lateinit var btnNotif: Button
    private lateinit var btnBattery: Button
    private lateinit var tvVersionHeader: TextView
    private lateinit var btnUpdateHeader: Button
    private lateinit var logTextView: TextView
    
    private var timeRanges = mutableListOf<JSONObject>()
    private var selectedApps = mutableSetOf<String>()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
        when (key) {
            "master_on" -> {
                val isOn = sharedPrefs.getBoolean("master_on", true)
                if (switchMaster.isChecked != isOn) switchMaster.isChecked = isOn
                if (::cardMaster.isInitialized) updateCardTheme(cardMaster, isOn)
            }
            "ai_on" -> {
                val isOn = sharedPrefs.getBoolean("ai_on", true)
                if (switchAI.isChecked != isOn) switchAI.isChecked = isOn
                if (::cardAI.isInitialized) updateCardTheme(cardAI, isOn)
            }
            "manual_busy_on" -> {
                val isOn = sharedPrefs.getBoolean("manual_busy_on", false)
                if (switchManualBusy.isChecked != isOn) switchManualBusy.isChecked = isOn
                if (::cardManual.isInitialized) updateCardTheme(cardManual, isOn)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        
        loadRanges()
        selectedApps = prefs.getStringSet("allowed_apps", setOf("com.facebook.orca", "org.telegram.messenger", "com.discord", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill"))?.toMutableSet() ?: mutableSetOf()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F13"))
        }

        // --- HEADER ---
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#1C1C24"))
            elevation = 12f
        }
        
        val logoView = ImageView(this).apply {
            setImageResource(R.mipmap.logo)
            layoutParams = LinearLayout.LayoutParams(120, 120).apply { setMargins(0, 0, 30, 0) }
        }
        
        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val titleView = TextView(this).apply {
            text = "AUTOREPLY OSS"
            textSize = 16f
            setTextColor(Color.parseColor("#4CAF50"))
            paint.isFakeBoldText = true
        }

        val updatePrefs = getSharedPreferences("autoreply_update", Context.MODE_PRIVATE)
        var displayVersion = updatePrefs.getString("last_tag", "") ?: ""
        if (displayVersion.isEmpty()) {
            displayVersion = try { "v" + packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "v1.2" }
            updatePrefs.edit().putString("last_tag", displayVersion).apply()
        }

        tvVersionHeader = TextView(this).apply {
            text = displayVersion
            textSize = 12f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, 5, 0, 0)
            setOnClickListener {
                checkForUpdate(silent = false)
            }
        }

        titleContainer.addView(titleView)
        titleContainer.addView(tvVersionHeader)

        btnUpdateHeader = Button(this).apply {
            text = "UPDATE"
            background = createRoundBg("#FF9800", 50f)
            setTextColor(Color.WHITE)
            textSize = 12f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 90)
            setPadding(30, 0, 30, 0)
            visibility = View.GONE
        }

        headerLayout.addView(logoView)
        headerLayout.addView(titleContainer)
        headerLayout.addView(btnUpdateHeader)
        
        // --- TAB BAR ---
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#121215"))
        }

        val btnTab1 = Button(this).apply { text = "CONTROLS"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val btnTab2 = Button(this).apply { text = "SETTINGS"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val btnTab3 = Button(this).apply { text = "LOGS"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        
        tabBar.addView(btnTab1)
        tabBar.addView(btnTab2)
        tabBar.addView(btnTab3)

        // --- CONTENT FRAME ---
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // ==================== TAB 1: CONTROLS ====================
        val scrollTab1 = ScrollView(this)
        val tab1Layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 60)
            layoutTransition = LayoutTransition()
        }
        
        // Permissions & Battery
        btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 40) }
        btnNotif = Button(this).apply {
            text = "Notification Access"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,10,0) }
            background = createRoundBg("#2B2B36")
            setTextColor(Color.WHITE)
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        btnBattery = Button(this).apply {
            text = "Disable Battery Opt"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(10,0,0,0) }
            background = createRoundBg("#D32F2F")
            setTextColor(Color.WHITE)
            setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") }) }
                catch (e: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        }
        btnRow.addView(btnNotif)
        btnRow.addView(btnBattery)
        tab1Layout.addView(btnRow)

        // Switches
        cardMaster = createCard()
        switchMaster = Switch(this).apply {
            text = "ALL (Master Switch)"
            setTextColor(Color.WHITE); textSize = 16f; paint.isFakeBoldText = true; setPadding(30, 40, 30, 40)
            isChecked = prefs.getBoolean("master_on", true)
        }
        cardMaster.addView(switchMaster)
        tab1Layout.addView(cardMaster)
        updateCardTheme(cardMaster, switchMaster.isChecked)

        cardAI = createCard()
        switchAI = Switch(this).apply {
            text = "AI (Smart Auto-Reply)"
            setTextColor(Color.parseColor("#BBBBBB")); textSize = 15f; setPadding(30, 40, 30, 40)
            isChecked = prefs.getBoolean("ai_on", true)
        }
        cardAI.addView(switchAI)
        tab1Layout.addView(cardAI)
        updateCardTheme(cardAI, switchAI.isChecked)

        cardManual = createCard()
        switchManualBusy = Switch(this).apply {
            text = "CHATBOT (Manual Busy Reply)"
            setTextColor(Color.parseColor("#FF9800")); textSize = 15f; paint.isFakeBoldText = true; setPadding(30, 40, 30, 40)
            isChecked = prefs.getBoolean("manual_busy_on", false)
        }
        cardManual.addView(switchManualBusy)
        tab1Layout.addView(cardManual)
        updateCardTheme(cardManual, switchManualBusy.isChecked)
        
        switchMaster.setOnCheckedChangeListener { _, isChecked -> 
            prefs.edit().putBoolean("master_on", isChecked).apply()
            updateCardTheme(cardMaster, isChecked)
            if (!isChecked) {
                switchAI.isChecked = false
                switchManualBusy.isChecked = false
            } else if (!switchAI.isChecked && !switchManualBusy.isChecked) {
                switchAI.isChecked = true 
            }
        }
        
        switchAI.setOnCheckedChangeListener { _, isChecked -> 
            prefs.edit().putBoolean("ai_on", isChecked).apply()
            updateCardTheme(cardAI, isChecked)
            if (isChecked) {
                switchMaster.isChecked = true
                switchManualBusy.isChecked = false
            } else if (!switchManualBusy.isChecked) {
                switchMaster.isChecked = false
            }
        }
        
        switchManualBusy.setOnCheckedChangeListener { _, isChecked -> 
            prefs.edit().putBoolean("manual_busy_on", isChecked).apply()
            updateCardTheme(cardManual, isChecked)
            if (isChecked) {
                prefs.edit().putLong("manual_busy_time", System.currentTimeMillis()).apply()
            }
            if (::rangesContainer.isInitialized) {
                rangesContainer.alpha = if (isChecked) 0.3f else 1.0f
            }
            if (isChecked) {
                switchMaster.isChecked = true
                switchAI.isChecked = false
            } else if (!switchAI.isChecked) {
                switchMaster.isChecked = false
            }
        }

        // Remote Commands Table
        val cardRemoteCommands = createCard()
        cardRemoteCommands.addView(TextView(this).apply {
            text = "🛠️ REMOTE CONTROL COMMANDS:"
            setTextColor(Color.WHITE); textSize = 15f; paint.isFakeBoldText = true; setPadding(40, 30, 40, 10)
        })
        cardRemoteCommands.addView(TextView(this).apply {
            text = """
• STATUS / CHECK : System status report
• ENABLE_ALL / DISABLE_ALL : Toggle master switch
• ENABLE_AI / DISABLE_AI : Toggle AI replies
• ENABLE_CHATBOT / DISABLE_CHATBOT : Toggle busy chatbot
• CHATBOT: <message> : Update busy reply text
• AI: <prompt> : Update AI personality prompt
• YES_NAME: <names> : Whitelist contacts (empty to clear)
• NO_NAME: <names> : Blacklist contacts (empty to clear)
• MY_NAME: <names> : Update recognized owner names
• HELP : View available commands
            """.trimIndent()
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 12f
            setPadding(40, 0, 40, 30)
            setLineSpacing(8f, 1f)
        })
        tab1Layout.addView(cardRemoteCommands)

        scrollTab1.addView(tab1Layout)


        // ==================== TAB 2: SETTINGS ====================
        val scrollTab2 = ScrollView(this).apply { visibility = View.GONE }
        val tab2Layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 60)
            layoutTransition = LayoutTransition()
            isFocusableInTouchMode = true
        }

        // 1. Server Configuration
        val cardServer = createCard()
        cardServer.addView(TextView(this).apply {
            text = "🌐 SERVER & API CONFIGURATION:"
            setTextColor(Color.WHITE); textSize = 16f; paint.isFakeBoldText = true; setPadding(40, 30, 40, 10)
        })
        
        cardServer.addView(TextView(this).apply {
            text = "Vercel Backend URL:"
            setTextColor(Color.parseColor("#81C784")); textSize = 13f; setPadding(40, 10, 40, 0)
        })
        editServerUrl = EditText(this).apply {
            setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY)
            setHint("https://your-server.vercel.app")
            setText(prefs.getString("server_url", ""))
            setPadding(40, 10, 40, 20); minLines = 1; background = null
        }
        cardServer.addView(editServerUrl)

        cardServer.addView(TextView(this).apply {
            text = "Bot Secret (x-bot-secret):"
            setTextColor(Color.parseColor("#81C784")); textSize = 13f; setPadding(40, 10, 40, 0)
        })
        editBotSecret = EditText(this).apply {
            setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY)
            setHint("YOUR_BOT_SECRET_HERE")
            setText(prefs.getString("bot_secret", ""))
            setPadding(40, 10, 40, 20); minLines = 1; background = null
        }
        cardServer.addView(editBotSecret)

        val btnTestServer = Button(this).apply {
            text = "⚡ TEST SERVER CONNECTION"
            background = createRoundBg("#37474F", 20f)
            setTextColor(Color.parseColor("#80D8FF"))
            textSize = 13f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 110).apply { setMargins(40, 10, 40, 30) }
            setOnClickListener { testServerConnection() }
        }
        cardServer.addView(btnTestServer)
        tab2Layout.addView(cardServer)

        // 2. Recognized Names
        val cardMyNames = createCard()
        cardMyNames.addView(TextView(this).apply {
            text = "👤 RECOGNIZED NAMES (MY_NAMES):"
            setTextColor(Color.WHITE); textSize = 16f; paint.isFakeBoldText = true; setPadding(40, 30, 40, 10)
        })
        cardMyNames.addView(TextView(this).apply {
            text = "Enter your display names/aliases (comma-separated). Used to recognize your own outgoing messages (prevents self-replies) and authorize remote control commands:"
            setTextColor(Color.parseColor("#90A4AE")); textSize = 12f; setPadding(40, 0, 40, 10)
        })
        editMyNames = EditText(this).apply {
            setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY)
            setHint("You, Owner, Admin, ...")
            setText(prefs.getString("my_names", ""))
            setPadding(40, 10, 40, 30); minLines = 1; background = null
        }
        cardMyNames.addView(editMyNames)
        tab2Layout.addView(cardMyNames)

        // 3. Supported Apps
        val cardApp = createCard()
        val appHeaderLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(40, 30, 40, 10); gravity = Gravity.CENTER_VERTICAL }
        appHeaderLayout.addView(TextView(this).apply { text = "📱 SUPPORTED APPS:"; setTextColor(Color.WHITE); textSize = 16f; paint.isFakeBoldText = true; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        appHeaderLayout.addView(Button(this).apply { text = "+ ADD APP"; background = createRoundBg("#03A9F4"); setTextColor(Color.WHITE); textSize = 12f; setPadding(20,0,20,0); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 90); setOnClickListener { showAddAppDialog() } })
        cardApp.addView(appHeaderLayout)
        appsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 0, 40, 30); layoutTransition = LayoutTransition() }
        cardApp.addView(appsContainer)
        tab2Layout.addView(cardApp)
        renderApps()

        // 4. YES_NAME Whitelist
        val cardYesName = createCard()
        cardYesName.addView(TextView(this).apply {
            text = "✅ YES_NAME (Whitelist Contacts):"
            setTextColor(Color.WHITE); textSize = 16f; paint.isFakeBoldText = true; setPadding(40, 30, 40, 10)
        })
        editYesName = EditText(this).apply {
            setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY)
            setHint("Name 1, Name 2 (empty = reply to all)")
            setText(prefs.getString("yes_name", ""))
            setPadding(40, 10, 40, 30); minLines = 2; background = null
        }
        cardYesName.addView(editYesName)
        tab2Layout.addView(cardYesName)

        // 5. NO_NAME Blacklist
        val cardNoName = createCard()
        cardNoName.addView(TextView(this).apply {
            text = "🚫 NO_NAME (Blacklist Contacts):"
            setTextColor(Color.WHITE); textSize = 16f; paint.isFakeBoldText = true; setPadding(40, 30, 40, 10)
        })
        editNoName = EditText(this).apply {
            setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY)
            setHint("Spam, Telemarketer, Unknown...")
            setText(prefs.getString("no_name", ""))
            setPadding(40, 10, 40, 30); minLines = 2; background = null
        }
        cardNoName.addView(editNoName)
        tab2Layout.addView(cardNoName)

        // 6. Automatic Busy Schedule
        val cardTime = createCard()
        val timeHeaderLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(40, 30, 40, 10); gravity = Gravity.CENTER_VERTICAL }
        timeHeaderLayout.addView(TextView(this).apply { text = "⏰ BUSY SCHEDULE (HOURS):"; setTextColor(Color.WHITE); textSize = 16f; paint.isFakeBoldText = true; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        timeHeaderLayout.addView(Button(this).apply { text = "+ ADD"; background = createRoundBg("#4CAF50"); setTextColor(Color.WHITE); textSize = 12f; setPadding(20,0,20,0); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 90); setOnClickListener { timeRanges.add(JSONObject().put("sh", 0).put("sm", 0).put("eh", 0).put("em", 0)); renderTimeRanges() } })
        cardTime.addView(timeHeaderLayout)
        rangesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 0, 40, 30); layoutTransition = LayoutTransition(); alpha = if (switchManualBusy.isChecked) 0.3f else 1.0f }
        cardTime.addView(rangesContainer)
        tab2Layout.addView(cardTime)
        renderTimeRanges()

        // 7. Busy Message
        val defaultBusyMsg = "Sorry, I am currently busy and cannot reply immediately!\nYou can leave a message or reach me at:\n- YOUR NAME\n- Phone: YOUR PHONE\n- Email: YOUR EMAIL\n\n(Automated reply)"
        val cardMsg = createCard()
        cardMsg.addView(TextView(this).apply { text = "📝 BUSY MESSAGE (CHATBOT):"; setTextColor(Color.WHITE); textSize = 16f; paint.isFakeBoldText = true; setPadding(40, 30, 40, 10) })
        editBusyMsg = EditText(this).apply { setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY); setText(prefs.getString("busy_msg", defaultBusyMsg)); setPadding(40, 10, 40, 30); minLines = 3; background = null }
        cardMsg.addView(editBusyMsg)
        tab2Layout.addView(cardMsg)

        // 8. AI Prompt
        val cardPrompt = createCard()
        cardPrompt.addView(TextView(this).apply {
            text = "🧠 AI PERSONALITY (PROMPT):"
            setTextColor(Color.WHITE); textSize = 16f; paint.isFakeBoldText = true; setPadding(40, 30, 40, 10)
        })
        val defaultPrompt = "You are a friendly and helpful assistant. Reply naturally and casually in 1 short sentence. Never reveal that you are an AI. Do not use markdown."
        editPrompt = EditText(this).apply {
            setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY)
            setText(prefs.getString("ai_prompt", defaultPrompt))
            setPadding(40, 10, 40, 30); minLines = 4; background = null
        }
        cardPrompt.addView(editPrompt)
        tab2Layout.addView(cardPrompt)

        // Save Button
        tab2Layout.addView(Button(this).apply {
            text = "💾 SAVE ALL SETTINGS"
            background = createRoundBg("#2196F3", 100f)
            setTextColor(Color.WHITE)
            textSize = 16f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140).apply { setMargins(0, 20, 0, 40) }
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Save Confirmation").setMessage("Save all configuration changes?").setPositiveButton("SAVE") { _, _ ->
                    prefs.edit()
                        .putString("server_url", editServerUrl.text.toString().trim())
                        .putString("bot_secret", editBotSecret.text.toString().trim())
                        .putString("my_names", editMyNames.text.toString().trim())
                        .putString("busy_msg", editBusyMsg.text.toString())
                        .putString("ai_prompt", editPrompt.text.toString())
                        .putString("yes_name", editYesName.text.toString())
                        .putString("no_name", editNoName.text.toString())
                        .apply()
                    saveRanges()
                    
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    currentFocus?.let { view -> imm.hideSoftInputFromWindow(view.windowToken, 0); view.clearFocus() }
                    
                    Toast.makeText(this@MainActivity, "✅ All settings saved successfully!", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("CANCEL", null).show()
            }
        })
        scrollTab2.addView(tab2Layout)


        // ==================== TAB 3: LOGS ====================
        val scrollTab3 = ScrollView(this).apply { visibility = View.GONE }
        val tab3Layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 60) }
        
        // Action Bar for Logs
        val logActionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 30)
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnClearLog = Button(this).apply {
            text = "🗑️ CLEAR LOGS"
            background = createRoundBg("#D32F2F", 20f)
            setTextColor(Color.WHITE)
            textSize = 12f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply { setMargins(0, 0, 10, 0) }
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Clear Logs")
                    .setMessage("Are you sure you want to clear all recorded activity logs?")
                    .setPositiveButton("CLEAR") { _, _ ->
                        AppLog.clear(this@MainActivity)
                        logTextView.text = "--- 📋 SYSTEM LOGS ---\n\n(No logs recorded yet)"
                        Toast.makeText(this@MainActivity, "Logs cleared successfully!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("CANCEL", null)
                    .show()
            }
        }
        val btnCopyLog = Button(this).apply {
            text = "📋 COPY LOGS"
            background = createRoundBg("#2B2B36", 20f)
            setTextColor(Color.WHITE)
            textSize = 12f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply { setMargins(10, 0, 0, 0) }
            setOnClickListener {
                val fullLogs = AppLog.load(this@MainActivity)
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AutoReply Logs", fullLogs))
                Toast.makeText(this@MainActivity, "📋 Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        }
        logActionBar.addView(btnClearLog)
        logActionBar.addView(btnCopyLog)
        tab3Layout.addView(logActionBar)

        val logContainer = createCard()
        logTextView = TextView(this).apply { 
            val oldLogs = AppLog.load(this@MainActivity)
            text = if (oldLogs.isEmpty()) "--- 📋 SYSTEM LOGS ---\n\n(No logs recorded yet)" else "--- 📋 SYSTEM LOGS ---\n\n" + oldLogs
            textSize = 12f
            setTextColor(Color.parseColor("#81C784"))
            setPadding(40, 40, 40, 40) 
            setLineSpacing(4f, 1f)
        }
        logContainer.addView(logTextView)
        tab3Layout.addView(logContainer)
        scrollTab3.addView(tab3Layout)

        // Tab Switching logic
        fun updateTabStyles(selectedBtn: Button) {
            btnTab1.background = null; btnTab1.setTextColor(Color.parseColor("#888888"))
            btnTab2.background = null; btnTab2.setTextColor(Color.parseColor("#888888"))
            btnTab3.background = null; btnTab3.setTextColor(Color.parseColor("#888888"))
            
            selectedBtn.background = createRoundBg("#2B2B36", 50f)
            selectedBtn.setTextColor(Color.WHITE)
        }
        updateTabStyles(btnTab1)

        btnTab1.setOnClickListener { updateTabStyles(btnTab1); scrollTab1.visibility = View.VISIBLE; scrollTab2.visibility = View.GONE; scrollTab3.visibility = View.GONE }
        btnTab2.setOnClickListener { updateTabStyles(btnTab2); scrollTab1.visibility = View.GONE; scrollTab2.visibility = View.VISIBLE; scrollTab3.visibility = View.GONE }
        btnTab3.setOnClickListener { updateTabStyles(btnTab3); scrollTab1.visibility = View.GONE; scrollTab2.visibility = View.GONE; scrollTab3.visibility = View.VISIBLE; scrollTab3.post { scrollTab3.fullScroll(ScrollView.FOCUS_DOWN) } }

        contentFrame.addView(scrollTab1)
        contentFrame.addView(scrollTab2)
        contentFrame.addView(scrollTab3)

        rootLayout.addView(headerLayout)
        rootLayout.addView(tabBar)
        rootLayout.addView(contentFrame)
        setContentView(rootLayout)

        scope.launch {
            AppLog.logs.collect { msg ->
                if (msg == "__CLEARED__") {
                    logTextView.text = "--- 📋 SYSTEM LOGS ---\n\n(No logs recorded yet)"
                } else {
                    if (logTextView.text.contains("(No logs recorded yet)")) {
                        logTextView.text = "--- 📋 SYSTEM LOGS ---\n\n" + msg + "\n\n"
                    } else {
                        logTextView.append(msg + "\n\n")
                    }
                    if (scrollTab3.visibility == View.VISIBLE) {
                        scrollTab3.post { scrollTab3.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
        }

        checkForUpdate(silent = true)
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        if (!::btnRow.isInitialized) return
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val isBatteryOptIgnored = pm.isIgnoringBatteryOptimizations(packageName)
        val notifListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val isNotifGranted = notifListeners.contains(packageName)

        btnNotif.visibility = if (isNotifGranted) View.GONE else View.VISIBLE
        btnBattery.visibility = if (isBatteryOptIgnored) View.GONE else View.VISIBLE

        if (isNotifGranted && isBatteryOptIgnored) {
            btnRow.visibility = View.GONE
        } else {
            btnRow.visibility = View.VISIBLE
        }
    }

    private fun testServerConnection() {
        val serverUrl = editServerUrl.text.toString().trim().removeSuffix("/")
        val botSecret = editBotSecret.text.toString().trim()
        
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Please enter a Server URL first!", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Connecting to server...", Toast.LENGTH_SHORT).show()
        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
                val req = Request.Builder().url("$serverUrl/api/version").build()
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    withContext(Dispatchers.Main) {
                        if (res.isSuccessful) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("✅ Connection Successful")
                                .setMessage("Server is live and reachable!\nServer Response: $body")
                                .setPositiveButton("OK", null)
                                .show()
                        } else {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("⚠️ Server Error (" + res.code + ")")
                                .setMessage("Server responded with HTTP error " + res.code + ".\nPlease check your URL and Vercel deployment.")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("❌ Connection Failed")
                        .setMessage("Could not connect to server:\n" + e.message + "\n\nPlease check your network and domain.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun checkForUpdate(silent: Boolean) {
        if (!silent) Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        val versionUrl = UpdateChecker.getVersionUrl(this)

        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
                val req = Request.Builder().url(versionUrl).build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        if (!silent) withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "No updates found!", Toast.LENGTH_SHORT).show() }
                        return@use
                    }
                    val bodyStr = res.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    
                    val remoteTag = json.optString("version", "")
                    val notes = json.optString("changelog", "No release notes available.")
                    val link = json.optString("url", "")
                    
                    val updatePrefs = getSharedPreferences("autoreply_update", Context.MODE_PRIVATE)
                    val savedTag = updatePrefs.getString("last_tag", "") ?: ""
                    
                    withContext(Dispatchers.Main) {
                        if (remoteTag.isNotEmpty() && link.isNotEmpty() && remoteTag != savedTag) {
                            val doUpdate = {
                                updatePrefs.edit().putString("last_tag", remoteTag).apply()
                                tvVersionHeader.text = remoteTag
                                btnUpdateHeader.visibility = View.GONE
                                downloadAndInstallApk(link)
                            }

                            btnUpdateHeader.visibility = View.VISIBLE
                            btnUpdateHeader.setOnClickListener { doUpdate() }

                            if (!silent) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("🚀 Update Available: $remoteTag")
                                    .setMessage(notes)
                                    .setPositiveButton("INSTALL NOW") { _, _ -> doUpdate() }
                                    .setNegativeButton("LATER", null)
                                    .show()
                            }
                        } else {
                            btnUpdateHeader.visibility = View.GONE
                            if (!silent) Toast.makeText(this@MainActivity, "You are on the latest version ($savedTag)!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                if (!silent) withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Update check failed!", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundBg("#1C1C24", 32f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 30) }
            elevation = 8f
        }
    }

    private fun createRoundBg(colorCode: String, radius: Float = 24f): GradientDrawable {
        return GradientDrawable().apply { setColor(Color.parseColor(colorCode)); cornerRadius = radius }
    }

    private fun updateCardTheme(card: LinearLayout, isOn: Boolean) {
        card.background = createRoundBg(if (isOn) "#1B3B2B" else "#1C1C24", 32f)
    }

    private fun loadRanges() {
        val defaultRanges = "[{\"sh\":0,\"sm\":0,\"eh\":6,\"em\":35},{\"sh\":7,\"sm\":0,\"eh\":11,\"em\":0},{\"sh\":13,\"sm\":0,\"eh\":17,\"em\":30}]"
        val jsonStr = prefs.getString("busy_ranges", defaultRanges) ?: defaultRanges
        timeRanges.clear()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) timeRanges.add(arr.getJSONObject(i))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveRanges() {
        val arr = JSONArray()
        timeRanges.forEach { arr.put(it) }
        prefs.edit().putString("busy_ranges", arr.toString()).apply()
    }

    @SuppressLint("DefaultLocale")
    private fun renderTimeRanges() {
        rangesContainer.removeAllViews()
        for (i in timeRanges.indices) {
            val obj = timeRanges[i]
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 15, 0, 15) }
            val tvTime = TextView(this).apply {
                text = "⏱️ ${String.format("%02d:%02d", obj.getInt("sh"), obj.getInt("sm"))}  ➜  ${String.format("%02d:%02d", obj.getInt("eh"), obj.getInt("em"))}"
                setTextColor(Color.parseColor("#FFCA28")); textSize = 15f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    TimePickerDialog(this@MainActivity, { _, h, m ->
                        obj.put("sh", h); obj.put("sm", m)
                        TimePickerDialog(this@MainActivity, { _, eh2, em2 ->
                            obj.put("eh", eh2); obj.put("em", em2); renderTimeRanges()
                        }, obj.getInt("eh"), obj.getInt("em"), true).show()
                    }, obj.getInt("sh"), obj.getInt("sm"), true).show()
                }
            }
            val btnDel = Button(this).apply {
                text = "REMOVE"; setTextColor(Color.WHITE); textSize = 12f; background = createRoundBg("#424242", 16f)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 90)
                setOnClickListener { timeRanges.removeAt(i); renderTimeRanges() }
            }
            row.addView(tvTime); row.addView(btnDel); rangesContainer.addView(row)
        }
    }

    private fun renderApps() {
        appsContainer.removeAllViews()
        val pm = packageManager
        
        scope.launch(Dispatchers.IO) {
            val appList = selectedApps.toList()
            
            for (pkg in appList) {
                var appName = pkg; var appIcon: Drawable? = null
                try { val info = pm.getApplicationInfo(pkg, 0); appName = pm.getApplicationLabel(info).toString(); appIcon = pm.getApplicationIcon(info) } catch (e: Exception) {}
                
                withContext(Dispatchers.Main) {
                    val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 15, 0, 15) }
                    val ivIcon = ImageView(this@MainActivity).apply { if (appIcon != null) setImageDrawable(appIcon); layoutParams = LinearLayout.LayoutParams(80, 80).apply { setMargins(0, 0, 20, 0) } }
                    val tvApp = TextView(this@MainActivity).apply { text = appName; setTextColor(Color.parseColor("#29B6F6")); textSize = 15f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                    val btnDel = Button(this@MainActivity).apply {
                        text = "REMOVE"; setTextColor(Color.WHITE); textSize = 12f; background = createRoundBg("#424242", 16f)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 90)
                        setOnClickListener { selectedApps.remove(pkg); prefs.edit().putStringSet("allowed_apps", selectedApps).apply(); renderApps() }
                    }
                    row.addView(ivIcon); row.addView(tvApp); row.addView(btnDel)
                    appsContainer.addView(row)
                }
            }
        }
    }

    private fun showAddAppDialog() {
        Toast.makeText(this, "Loading installed apps...", Toast.LENGTH_SHORT).show()
        
        scope.launch(Dispatchers.IO) {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val apps = pm.queryIntentActivities(intent, 0)
            
            val appData = apps.map { 
                Triple(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) 
            }.sortedBy { it.first.lowercase() }
            
            val tempSelectedApps = mutableSetOf<String>().apply { addAll(selectedApps) }
            
            withContext(Dispatchers.Main) {
                val scrollView = ScrollView(this@MainActivity)
                val listLayout = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(40, 20, 40, 20)
                }

                for (app in appData) {
                    val appName = app.first
                    val pkg = app.second
                    val icon = app.third

                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 20, 0, 20)
                        isClickable = true
                    }

                    val cb = CheckBox(this@MainActivity).apply {
                        isChecked = tempSelectedApps.contains(pkg)
                        isClickable = false
                    }

                    val iv = ImageView(this@MainActivity).apply {
                        setImageDrawable(icon)
                        layoutParams = LinearLayout.LayoutParams(80, 80).apply { setMargins(20, 0, 30, 0) }
                    }

                    val tv = TextView(this@MainActivity).apply {
                        text = appName
                        setTextColor(Color.WHITE)
                        textSize = 16f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    row.addView(cb)
                    row.addView(iv)
                    row.addView(tv)

                    row.setOnClickListener {
                        cb.isChecked = !cb.isChecked
                        if (cb.isChecked) tempSelectedApps.add(pkg) else tempSelectedApps.remove(pkg)
                    }

                    listLayout.addView(row)
                }
                
                scrollView.addView(listLayout)

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Select Supported Apps")
                    .setView(scrollView)
                    .setPositiveButton("DONE") { _, _ ->
                        selectedApps.clear()
                        selectedApps.addAll(tempSelectedApps)
                        prefs.edit().putStringSet("allowed_apps", selectedApps).apply()
                        renderApps()
                    }
                    .setNegativeButton("CANCEL", null)
                    .show()
            }
        }
    }

    private fun downloadAndInstallApk(url: String) {
        Toast.makeText(this, "Downloading update, please wait...", Toast.LENGTH_LONG).show()
        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Failed to download APK (" + response.code + ")")
                    val apkFile = File(cacheDir, "autoreply_update.apk")
                    response.body?.byteStream()?.use { input ->
                        apkFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        installApk(apkFile)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Download error: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun installApk(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                Toast.makeText(this, "Please allow installing unknown apps and tap UPDATE again!", Toast.LENGTH_LONG).show()
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        scope.cancel()
    }
}
