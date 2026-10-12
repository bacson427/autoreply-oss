package io.github.autoreply.oss

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object Config {
    const val DEFAULT_SERVER_URL = "https://your-server.vercel.app"
    const val DEFAULT_LOG_URL = "https://your-server.vercel.app/api/logs"
    const val COOLDOWN_MS = 2_000L
}

object AntiSpamState {
    val lastReplyAt = ConcurrentHashMap<String, Long>()
    val lastMsgHashMap = ConcurrentHashMap<String, Long>()
    val busyNotifiedMap = ConcurrentHashMap<String, Int>()
    val lock = Any()
}

class ReplyService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)

        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        if (extras.getBoolean("android.isGroupConversation", false)) return

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        if (text.isEmpty()) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

        val action = n.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }

        // --- DYNAMIC CONFIGURATION ---
        val serverBase = prefs.getString("server_url", "")?.trim()?.removeSuffix("/") ?: ""
        val replyUrl = if (serverBase.isNotEmpty()) "$serverBase/api/reply" else "${Config.DEFAULT_SERVER_URL}/api/reply"
        val logUrl = if (serverBase.isNotEmpty()) "$serverBase/api/logs" else Config.DEFAULT_LOG_URL
        val botSecret = prefs.getString("bot_secret", "")?.trim()?.ifEmpty { BuildConfig.BOT_SECRET } ?: BuildConfig.BOT_SECRET

        val myNamesStr = prefs.getString("my_names", "")?.trim() ?: ""
        val configuredNames = if (myNamesStr.isNotEmpty()) {
            myNamesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else emptyList()
        val allMyNames = (listOf("You", "Bạn", "Owner", "Admin", "Me") + configuredNames).distinct()

        // --- 1. REMOTE CONTROL COMMANDS ---
        val upperText = text.trim().uppercase()
        val isRemoteCommand = upperText == "STATUS" || upperText == "CHECK" || upperText == "KIEM_TRA" || 
                              upperText == "ENABLE_ALL" || upperText == "BAT_ALL" || 
                              upperText == "DISABLE_ALL" || upperText == "TAT_ALL" || 
                              upperText == "ENABLE_AI" || upperText == "BAT_AI" || 
                              upperText == "DISABLE_AI" || upperText == "TAT_AI" || 
                              upperText == "ENABLE_CHATBOT" || upperText == "BAT_CHATBOT" || 
                              upperText == "DISABLE_CHATBOT" || upperText == "TAT_CHATBOT" ||
                              upperText == "HELP" || upperText == "TRO_GIUP" ||
                              text.startsWith("CHATBOT:", ignoreCase = true) ||
                              text.startsWith("AI:", ignoreCase = true) ||
                              text.startsWith("PROMPT_AI:", ignoreCase = true) ||
                              text.startsWith("YES_NAME:", ignoreCase = true) || text.startsWith("YES NAME:", ignoreCase = true) ||
                              text.startsWith("NO_NAME:", ignoreCase = true) || text.startsWith("NO NAME:", ignoreCase = true) ||
                              text.startsWith("MY_NAME:", ignoreCase = true) || text.startsWith("MY_NAMES:", ignoreCase = true) ||
                              text.startsWith("TEN:", ignoreCase = true)
                                
        if (isRemoteCommand) {
            if (allMyNames.any { title.contains(it, ignoreCase = true) }) {
                var replyMessage = ""
                val defaultBusyMsg = "Sorry, I am currently busy and cannot reply immediately!\nYou can leave a message or reach me at:\n- YOUR NAME\n- Phone: YOUR PHONE\n- Email: YOUR EMAIL\n\n(Automated reply)"
                val defaultPrompt = "You are a friendly and helpful assistant. Reply naturally and casually in 1 short sentence. Never reveal that you are an AI. Do not use markdown."

                when {
                    upperText == "STATUS" || upperText == "CHECK" || upperText == "KIEM_TRA" -> {
                        val mSt = if (prefs.getBoolean("master_on", true)) "ON" else "OFF"
                        val aSt = if (prefs.getBoolean("ai_on", true)) "ON" else "OFF"
                        val cSt = if (prefs.getBoolean("manual_busy_on", false)) "ON" else "OFF"
                        val curMsg = prefs.getString("busy_msg", defaultBusyMsg)
                        val curPrompt = prefs.getString("ai_prompt", defaultPrompt)
                        val curYes = prefs.getString("yes_name", "")?.ifEmpty { "Everyone" } ?: "Everyone"
                        val curNo = prefs.getString("no_name", "")?.ifEmpty { "None" } ?: "None"
                        val curNames = allMyNames.joinToString(", ")
                        
                        replyMessage = "📊 SYSTEM STATUS\n[ALL: $mSt] | [AI: $aSt] | [CHATBOT: $cSt]\n👤 Owner Names: $curNames\n✅ YES_NAME: $curYes\n🚫 NO_NAME: $curNo\n\n📝 CHATBOT:\n$curMsg\n\n🧠 AI PROMPT:\n$curPrompt"
                    }
                    upperText == "HELP" || upperText == "TRO_GIUP" -> {
                        replyMessage = "🛠️ AVAILABLE COMMANDS:\n• STATUS / CHECK: System status report\n• ENABLE_ALL / DISABLE_ALL: Toggle master switch\n• ENABLE_AI / DISABLE_AI: Toggle AI replies\n• ENABLE_CHATBOT / DISABLE_CHATBOT: Toggle Chatbot\n• CHATBOT: <text>: Set busy reply message\n• AI: <prompt>: Set AI prompt\n• YES_NAME: <names>: Whitelist contacts\n• NO_NAME: <names>: Blacklist contacts\n• MY_NAME: <names>: Set recognized owner names"
                    }
                    text.startsWith("YES_NAME:", ignoreCase = true) || text.startsWith("YES NAME:", ignoreCase = true) || text.startsWith("TEN:", ignoreCase = true) -> {
                        val newNames = text.substringAfter(":").trim()
                        prefs.edit().putString("yes_name", newNames).apply()
                        replyMessage = "✅ Updated YES_NAME to:\n${newNames.ifEmpty { "Everyone" }}"
                    }
                    text.startsWith("NO_NAME:", ignoreCase = true) || text.startsWith("NO NAME:", ignoreCase = true) -> {
                        val newNames = text.substringAfter(":").trim()
                        prefs.edit().putString("no_name", newNames).apply()
                        replyMessage = "✅ Updated NO_NAME to:\n${newNames.ifEmpty { "None" }}"
                    }
                    text.startsWith("MY_NAME:", ignoreCase = true) || text.startsWith("MY_NAMES:", ignoreCase = true) -> {
                        val newNames = text.substringAfter(":").trim()
                        prefs.edit().putString("my_names", newNames).apply()
                        replyMessage = "✅ Updated recognized Owner Names to:\n$newNames"
                    }
                    text.startsWith("CHATBOT:", ignoreCase = true) -> {
                        val newMsg = text.substringAfter(":").trim()
                        if (newMsg.isNotEmpty()) {
                            prefs.edit().putString("busy_msg", newMsg).apply()
                            replyMessage = "✅ Updated CHATBOT message to:\n$newMsg"
                        }
                    }
                    text.startsWith("AI:", ignoreCase = true) || text.startsWith("PROMPT_AI:", ignoreCase = true) -> {
                        val newPrompt = text.substringAfter(":").trim()
                        if (newPrompt.isNotEmpty()) {
                            prefs.edit().putString("ai_prompt", newPrompt).apply()
                            replyMessage = "✅ Updated AI Prompt to:\n$newPrompt"
                        }
                    }
                    upperText == "ENABLE_ALL" || upperText == "BAT_ALL" -> {
                        prefs.edit().putBoolean("master_on", true).putBoolean("ai_on", true).putBoolean("manual_busy_on", false).apply()
                        replyMessage = "✅ ENABLED ALL\n[ALL: ON] | [AI: ON] | [CHATBOT: OFF]" 
                    }
                    upperText == "DISABLE_ALL" || upperText == "TAT_ALL" -> {
                        prefs.edit().putBoolean("master_on", false).putBoolean("ai_on", false).putBoolean("manual_busy_on", false).apply()
                        replyMessage = "✅ DISABLED ALL\n[ALL: OFF] | [AI: OFF] | [CHATBOT: OFF]" 
                    }
                    upperText == "ENABLE_AI" || upperText == "BAT_AI" -> {
                        prefs.edit().putBoolean("master_on", true).putBoolean("ai_on", true).putBoolean("manual_busy_on", false).apply()
                        replyMessage = "✅ ENABLED AI\n[ALL: ON] | [AI: ON] | [CHATBOT: OFF]" 
                    }
                    upperText == "DISABLE_AI" || upperText == "TAT_AI" -> {
                        prefs.edit().putBoolean("ai_on", false).apply()
                        val mSt = if (prefs.getBoolean("master_on", true)) "ON" else "OFF"
                        val cSt = if (prefs.getBoolean("manual_busy_on", false)) "ON" else "OFF"
                        replyMessage = "✅ DISABLED AI\n[ALL: $mSt] | [AI: OFF] | [CHATBOT: $cSt]" 
                    }
                    upperText == "ENABLE_CHATBOT" || upperText == "BAT_CHATBOT" -> {
                        prefs.edit().putBoolean("master_on", true).putBoolean("manual_busy_on", true).putLong("manual_busy_time", System.currentTimeMillis()).putBoolean("ai_on", false).apply()
                        replyMessage = "✅ ENABLED CHATBOT\n[ALL: ON] | [AI: OFF] | [CHATBOT: ON]" 
                    }
                    upperText == "DISABLE_CHATBOT" || upperText == "TAT_CHATBOT" -> {
                        prefs.edit().putBoolean("manual_busy_on", false).apply()
                        val mSt = if (prefs.getBoolean("master_on", true)) "ON" else "OFF"
                        val aSt = if (prefs.getBoolean("ai_on", true)) "ON" else "OFF"
                        replyMessage = "✅ DISABLED CHATBOT\n[ALL: $mSt] | [AI: $aSt] | [CHATBOT: OFF]" 
                    }
                }

                if (replyMessage.isNotEmpty()) {
                    AppLog.add(this@ReplyService, "🛠️", "REMOTE COMMAND", text)
                    if (action != null) sendReply(action, replyMessage)
                    return
                }
            }
        }

        // --- 2. MASTER SWITCH AND ALLOWED APPS CHECK ---
        if (!prefs.getBoolean("master_on", true)) return
        val allowedApps = prefs.getStringSet("allowed_apps", setOf("com.facebook.orca", "org.telegram.messenger", "com.discord", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill")) ?: setOf()
        if (sbn.packageName !in allowedApps) return

        // 1. NO_NAME Blacklist check
        val noNameStr = prefs.getString("no_name", "") ?: ""
        if (noNameStr.isNotBlank()) {
            val keywords = noNameStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (keywords.any { kw -> title.contains(kw, ignoreCase = true) }) {
                AppLog.add(this@ReplyService, "🚫", "NO_NAME Blocked", "$title - $text")
                sendLogToServer(logUrl, botSecret, "BLOCKED_NO_NAME", title, text)
                return
            }
        }

        // 2. YES_NAME Whitelist check
        val yesNameStr = prefs.getString("yes_name", "") ?: prefs.getString("allow_names", "") ?: ""
        if (yesNameStr.isNotBlank()) {
            val keywords = yesNameStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (!keywords.any { kw -> title.contains(kw, ignoreCase = true) }) {
                AppLog.add(this@ReplyService, "⚙️", "YES_NAME Ignored", "$title - $text")
                sendLogToServer(logUrl, botSecret, "IGNORED_YES_NAME", title, text)
                return
            }
        }

        val isAiEnabled = prefs.getBoolean("ai_on", true)
        val isManualBusy = prefs.getBoolean("manual_busy_on", false)
        val defaultBusyMsg = "Sorry, I am currently busy and cannot reply immediately!\nYou can leave a message or reach me at:\n- YOUR NAME\n- Phone: YOUR PHONE\n- Email: YOUR EMAIL\n\n(Automated reply)"
        val customBusyMessage = prefs.getString("busy_msg", defaultBusyMsg) ?: defaultBusyMsg
        val defaultRanges = "[{\"sh\":0,\"sm\":0,\"eh\":6,\"em\":35},{\"sh\":7,\"sm\":0,\"eh\":11,\"em\":0},{\"sh\":13,\"sm\":0,\"eh\":17,\"em\":30}]"
        val rangesJson = prefs.getString("busy_ranges", defaultRanges) ?: defaultRanges

        // Prevent loop spam
        if (text.contains("Automated reply", ignoreCase = true) || text.contains("Bot tự động trả lời", ignoreCase = true) || text.contains("✅ Updated", ignoreCase = true) || text.contains("✅ ENABLED", ignoreCase = true) || text.contains("✅ DISABLED", ignoreCase = true) || text.contains("[ALL:", ignoreCase = true) || text.contains("📊 SYSTEM STATUS", ignoreCase = true)) return
        
        // Self message recognition
        val ownText = allMyNames.firstNotNullOfOrNull { name ->
            val prefix = "$name:"
            if (text.startsWith(prefix, ignoreCase = true)) text.substring(prefix.length).trim() else null
        }
        
        if (ownText != null) {
            if (ownText.isNotEmpty()) {
                scope.launch { sendLogToServer(logUrl, botSecret, "SELF_MESSAGE", title, ownText) }
            }
            return
        }

        if (action == null) return

        val calendar = Calendar.getInstance()
        val timeInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        var sessionCode = -1
        if (isManualBusy) {
            sessionCode = (prefs.getLong("manual_busy_time", 99L) % 1000000).toInt()
        } else {
            try {
                val arr = JSONArray(rangesJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val startMins = obj.getInt("sh") * 60 + obj.getInt("sm")
                    val endMins = obj.getInt("eh") * 60 + obj.getInt("em")
                    val inRange = if (startMins <= endMins) timeInMinutes in startMins..endMins else timeInMinutes >= startMins || timeInMinutes <= endMins
                    if (inRange) { sessionCode = i; break }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        synchronized(AntiSpamState.lock) {
            val now = System.currentTimeMillis()
            val key = title 
            val msgHash = "${title}_${text}".hashCode().toLong()
            val lastMsgHash = AntiSpamState.lastMsgHashMap[key] ?: 0L
            val lastTime = AntiSpamState.lastReplyAt[key] ?: 0L

            if (now - lastTime < Config.COOLDOWN_MS) return
            if (msgHash == lastMsgHash && (now - lastTime < 5_000L)) return

            if (sessionCode != -1) {
                if (AntiSpamState.busyNotifiedMap[title] == sessionCode) return
                AntiSpamState.busyNotifiedMap[title] = sessionCode 
            }

            AntiSpamState.lastReplyAt[key] = now
            AntiSpamState.lastMsgHashMap[key] = msgHash
        }

        AppLog.add(this@ReplyService, "📩", "Received from $title", text)

        scope.launch {
            if (sessionCode == -1) {
                if (isAiEnabled) {
                    val reply = fetchReply(replyUrl, botSecret, title, text)
                    if (reply != null) {
                        sendReply(action, reply)
                        AppLog.add(this@ReplyService, "🤖", "AI Reply to $title", reply)
                        sendLogToServer(logUrl, botSecret, "BOT_AI", title, text, reply)
                    }
                } else {
                    AppLog.add(this@ReplyService, "⚙️", "Ignored $title", "Idle hours and AI is turned off")
                    sendLogToServer(logUrl, botSecret, "IGNORED", title, text)
                }
                return@launch
            }

            if (customBusyMessage.isNotBlank()) {
                sendReply(action, customBusyMessage)
                AppLog.add(this@ReplyService, "🤖", "Busy message sent to $title", customBusyMessage)
                sendLogToServer(logUrl, botSecret, "BUSY_REPLY", title, text, customBusyMessage)
            }
        }
    }

    private fun sendLogToServer(logUrl: String, botSecret: String, type: String, sender: String, message: String, reply: String = "") {
        try {
            val jsonStr = JSONObject()
                .put("type", type)
                .put("sender", sender)
                .put("message", message)
                .put("reply", reply)
                .toString()
                
            val req = Request.Builder().url(logUrl).addHeader("x-bot-secret", botSecret)
                .post(jsonStr.toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().close()
        } catch (e: Exception) {}
    }

    private fun fetchReply(replyUrl: String, botSecret: String, sender: String, text: String): String? {
        return try {
            val prefs = getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)
            val customPrompt = prefs.getString("ai_prompt", "") ?: ""

            val json = JSONObject()
                .put("sender", sender)
                .put("text", text)
                .put("prompt", customPrompt)
                .toString()

            val req = Request.Builder().url(replyUrl).addHeader("x-bot-secret", botSecret)
                .post(json.toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                JSONObject(body).optString("reply").trim().ifEmpty { null }
            }
        } catch (e: Exception) { null }
    }

    private fun sendReply(action: Notification.Action, reply: String) {
        try {
            val remoteInputs = action.remoteInputs ?: return
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            val results = Bundle().apply { remoteInputs.forEach { putCharSequence(it.resultKey, reply) } }
            RemoteInput.addResultsToIntent(remoteInputs, intent, results)
            RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            action.actionIntent.send(this, 0, intent)
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
