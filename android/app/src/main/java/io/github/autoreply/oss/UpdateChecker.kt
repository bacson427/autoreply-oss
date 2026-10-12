package io.github.autoreply.oss

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {
    private const val TAG = "AutoReplyUpdate"
    private const val CHANNEL = "update_channel"
    private const val NOTIF_ID = 9999
    private const val PREFS = "autoreply_update"
    private const val KEY_LAST_TAG = "last_tag"
    private const val KEY_LAST_CHECK = "last_check"
    private const val INTERVAL = 2 * 60 * 60 * 1000L // 2 hours

    fun getVersionUrl(context: Context): String {
        val prefs = context.getSharedPreferences("AutoReplyPrefs", Context.MODE_PRIVATE)
        val server = prefs.getString("server_url", "")?.trim()?.removeSuffix("/")
        return if (!server.isNullOrEmpty()) "$server/api/version" else "https://your-server.vercel.app/api/version"
    }

    fun check(context: Context, force: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                if (!force && (now - prefs.getLong(KEY_LAST_CHECK, 0L) < INTERVAL)) return@launch

                val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
                val req = Request.Builder().url(getVersionUrl(context)).build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@launch
                    val json = JSONObject(res.body?.string() ?: "{}")
                    val latestTag = json.optString("version")
                    val url = json.optString("url")
                    val notes = json.optString("changelog", "")
                    if (latestTag.isEmpty() || url.isEmpty()) return@launch

                    val savedTag = prefs.getString(KEY_LAST_TAG, "") ?: ""
                    if (savedTag.isEmpty()) {
                        prefs.edit().putString(KEY_LAST_TAG, latestTag).apply()
                    } else if (savedTag != latestTag) {
                        showNotif(context, latestTag, notes, url)
                        prefs.edit().putString(KEY_LAST_TAG, latestTag).apply()
                    }
                    prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
            }
        }
    }

    private fun showNotif(ctx: Context, tag: String, notes: String, url: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "App Updates", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AutoReply Update Available")
            .setContentText("Version: $tag - Tap to download and install")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                nm.notify(NOTIF_ID, notif)
            }
        } else {
            nm.notify(NOTIF_ID, notif)
        }
    }
}
