package io.github.autoreply.oss

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Config {
    val SERVER_URL: String = BuildConfig.SERVER_URL
    val SECRET: String = BuildConfig.BOT_SECRET

    val ALLOWED_PACKAGES = setOf(
        "com.facebook.orca",       // Messenger
        "org.telegram.messenger",  // Telegram
        "com.discord",             // Discord
    )

    const val COOLDOWN_MS = 2_000L

    // Your display names on each app - bot will skip messages starting with these
    val MY_NAMES = setOf(
        "Nguyen Bac Son",
        "Sơn",
        "Son",
    )

    // Remote commands (matched against message text from SELF sender)
    val COMMANDS = setOf(
        "BAT_ALL", "TAT_ALL",
        "BAT_AI", "TAT_AI",
        "BAT_CHATBOT", "TAT_CHATBOT",
    )
}

class ReplyService : NotificationListenerService() {

    private val tag = "AutoReply"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val lastReplyAt = HashMap<String, Long>()

    private fun isMyName(s: String): Boolean =
        Config.MY_NAMES.any { name ->
            s.startsWith("$name:", ignoreCase = true) ||
            s.startsWith("$name :", ignoreCase = true)
        }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in Config.ALLOWED_PACKAGES) return

        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        if (extras.getBoolean("android.isGroupConversation", false)) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        if (text.isEmpty()) return

        // Handle own messages (for history logging + remote commands)
        val ownText = when {
            text.startsWith("You:") -> text.removePrefix("You:").trim()
            text.startsWith("Bạn:") -> text.removePrefix("Bạn:").trim()
            isMyName(text) -> text.substringAfter(":").trim()
            else -> null
        }

        if (ownText != null) {
            if (ownText.isNotEmpty()) {
                if (ownText.uppercase() in Config.COMMANDS) {
                    scope.launch { sendCommand(ownText.uppercase()) }
                } else {
                    scope.launch { logSelf(title, ownText) }
                }
            }
            return
        }

        // Skip if title is one of my own names
        if (Config.MY_NAMES.any { it.equals(title, ignoreCase = true) }) return

        // Find reply action from notification
        val action = n.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true } ?: return

        // Cooldown per notification key
        val now = System.currentTimeMillis()
        val key = sbn.key
        val last = lastReplyAt[key] ?: 0L
        if (now - last < Config.COOLDOWN_MS) return
        lastReplyAt[key] = now

        scope.launch {
            val reply = fetchReply(title, text) ?: return@launch
            if (reply.isEmpty()) {
                Log.d(tag, "Bot is off, skipping")
                return@launch
            }
            sendReply(action, reply)
            logReply(title, text, reply)
        }
    }

    private fun sendCommand(cmd: String) {
        try {
            val json = JSONObject()
                .put("sender", "SELF")
                .put("text", cmd)
                .toString()

            val req = Request.Builder()
                .url(Config.SERVER_URL)
                .addHeader("x-bot-secret", Config.SECRET)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: return
                val reply = JSONObject(body).optString("reply")
                Log.d(tag, "Command $cmd result:\n$reply")
            }
        } catch (e: Exception) {
            Log.w(tag, "sendCommand error: ${e.message}")
        }
    }

    private fun fetchReply(sender: String, text: String): String? {
        return try {
            val json = JSONObject()
                .put("sender", sender)
                .put("text", text)
                .toString()

            val req = Request.Builder()
                .url(Config.SERVER_URL)
                .addHeader("x-bot-secret", Config.SECRET)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(tag, "Server returned ${res.code}")
                    return null
                }
                val body = res.body?.string() ?: return null
                JSONObject(body).optString("reply").trim()
            }
        } catch (e: Exception) {
            Log.w(tag, "fetchReply error: ${e.message}")
            null
        }
    }

    private fun sendReply(action: Notification.Action, reply: String) {
        try {
            val remoteInputs = action.remoteInputs ?: return
            val results = Bundle().apply {
                remoteInputs.forEach { putCharSequence(it.resultKey, reply) }
            }
            val intent = Intent()
            RemoteInput.addResultsToIntent(remoteInputs, intent, results)
            action.actionIntent.send(this, 0, intent)
        } catch (e: PendingIntent.CanceledException) {
            Log.w(tag, "Notification dismissed, cannot send reply")
        } catch (e: Exception) {
            Log.w(tag, "sendReply error: ${e.message}")
        }
    }

    private fun logReply(sender: String, incoming: String, reply: String) {
        try {
            val json = JSONObject()
                .put("type", "BOT_AI")
                .put("sender", sender)
                .put("message", incoming)
                .put("reply", reply)
                .toString()

            val req = Request.Builder()
                .url(Config.SERVER_URL.replace("/reply", "/logs"))
                .addHeader("x-bot-secret", Config.SECRET)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { }
        } catch (e: Exception) {
            Log.w(tag, "logReply error: ${e.message}")
        }
    }

    private fun logSelf(sender: String, text: String) {
        try {
            val json = JSONObject()
                .put("sender", sender)
                .put("text", text)
                .toString()

            val req = Request.Builder()
                .url(Config.SERVER_URL.replace("/reply", "/log-self"))
                .addHeader("x-bot-secret", Config.SECRET)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { }
        } catch (e: Exception) {
            Log.w(tag, "logSelf error: ${e.message}")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}