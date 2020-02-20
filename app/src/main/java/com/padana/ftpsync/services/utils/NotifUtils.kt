package com.padana.ftpsync.services.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.padana.ftpsync.MyApp
import com.padana.ftpsync.R
import com.padana.ftpsync.activities.ftp_connections.FtpConnectionsActivity

object NotifUtils {
    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(): String {
        val channelId = "my_service"
        val channelName = "Sync Background Servuce"
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lightColor = Color.BLUE
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = MyApp.getCtx().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    fun getNotification(title: String, content: String): Notification {
        val intent = Intent(MyApp.getCtx(), FtpConnectionsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(MyApp.getCtx(), 0, intent, 0)

        val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel()
                } else {
                    // If earlier version channel ID is not used
                    // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                    ""
                }
        val notificationBuilder = NotificationCompat.Builder(MyApp.getCtx(), channelId)
        return notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
    }
}