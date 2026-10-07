package io.github.autoreply.oss

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {
    private const val TAG = "AutoReplyUpdate"
    private const val CHANNEL_ID = "update_channel"
    private const val NOTIF_ID = 9999
    private const val PREFS = "autoreply_update"
    private const val KEY_LAST_TAG = "last_tag"
    private const val KEY_LAST_CHECK = "last_check"
    private const val CHECK_INTERVAL_MS = 2 * 60 * 60 * 1000L // 2 hours

    fun check(context: Context, force: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()

                // Throttle if not forced
                if (!force) {
                    val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
                    if (now - lastCheck < CHECK_INTERVAL_MS) return@launch
                }

                val versionUrl = Config.SERVER_URL.replace("/reply", "/version")
                val client = OkHttpClient.Builder()
                    .callTimeout(15, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url(versionUrl).build()

                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        Log.w(TAG, "Version check failed: ${res.code}")
                        return@launch
                    }

                    val json = JSONObject(res.body?.string() ?: "{}")
                    val latestTag = json.optString("version")
                    val downloadUrl = json.optString("url")
                    val changelog = json.optString("changelog", "")

                    if (latestTag.isEmpty() || downloadUrl.isEmpty()) return@launch

                    val savedTag = prefs.getString(KEY_LAST_TAG, "") ?: ""

                    // First launch: save current tag, don't notify
                    if (savedTag.isEmpty()) {
                        prefs.edit().putString(KEY_LAST_TAG, latestTag).apply()
                    } else if (savedTag != latestTag) {
                        showUpdateNotification(context, latestTag, changelog, downloadUrl)
                    }

                    prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check error: ${e.message}")
            }
        }
    }

    private fun showUpdateNotification(ctx: Context, tag: String, changelog: String, url: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App Updates", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val openIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AutoReply update available")
            .setContentText("Tap to download $tag")
            .setStyle(NotificationCompat.BigTextStyle().bigText(changelog.ifEmpty { "Tap to download the latest version." }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        nm.notify(NOTIF_ID, notif)
    }
}