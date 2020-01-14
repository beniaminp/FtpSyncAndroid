package com.padana.ftpsync.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MAX
import com.padana.ftpsync.R
import com.padana.ftpsync.activities.ftp_connections.FtpConnectionsActivity
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.SyncData
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream


class SyncDataService : Service() {
    lateinit var notificationManger: NotificationManager
    private val NOTIFICATION_ID = 101

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground()
        startSyncFolders().execute()
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartService = Intent(applicationContext,
                this.javaClass)
        restartService.setPackage(packageName)
        val restartServicePI = PendingIntent.getService(
                applicationContext, 1, restartService,
                PendingIntent.FLAG_ONE_SHOT)

        //Restart the service once it has been killed android


        //Restart the service once it has been killed android
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService[AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100] = restartServicePI

    }


    private fun startForeground() {
        notificationManger = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        startForeground(NOTIFICATION_ID, getNotification("FTP Sync", "Started in background..."))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "my_service"
        val channelName = "Ftp Sync Background Servuce"
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lightColor = Color.BLUE
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    private fun startSyncFolders(): AsyncTask<Void, Int, Void> {
        return object : AsyncTask<Void, Int, Void>() {
            @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
            override fun doInBackground(vararg voids: Void): Void? {
                var syncDataList = DatabaseClient(applicationContext).getAppDatabase().genericDAO.loadAllSyncData()
                var ftpClientsList = DatabaseClient(applicationContext).getAppDatabase().genericDAO.loadAllFtpClients()
                ftpClientsList.forEach { ftpClient ->
                    val syncDataForClient: List<SyncData> = getSyncDataForClient(ftpClient.id!!, syncDataList)

                    var ftp = FTPClient()
                    var config = FTPClientConfig()
                    ftp.defaultTimeout = 5000
                    ftp.configure(config)
                    try {
                        ftp.connect(ftpClient!!.server)

                        var reply = ftp.replyCode

                        if (!FTPReply.isPositiveCompletion(reply)) {
                            ftp.disconnect()
                            return null
                        }
                        ftp.autodetectUTF8 = true
                        ftp.controlEncoding = "UTF-8"

                        ftp.login(ftpClient.user, ftpClient.password)
                        syncDataForClient.forEach { syncData ->
                            var directoryExists = ftp.changeWorkingDirectory(syncData.serverPath)
                            if (!directoryExists) {
                                ftp.makeDirectory(syncData.serverPath)
                            }
                            var remoteFiles: Array<FTPFile> = ftp.listFiles(syncData.serverPath)
                            var localFiles: Array<File> = File(syncData.localPath).listFiles()
                            if (remoteFiles.size != localFiles.size) {
                                localFiles.forEach { localFile ->
                                    if (!remoteFileExists(localFile, remoteFiles)) {
                                        if (!localFile.isDirectory) {
                                            val bis = BufferedInputStream(FileInputStream(localFile))
                                            ftp.storeFile(syncData.serverPath + "/" + localFile.name, bis)
                                            bis.close()
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        startForeground(NOTIFICATION_ID, getNotification("ERROR", e.message.toString()))
                        System.err.println("Could not connect to server...")
                    }

                }
                return null
            }

            override fun onPostExecute(result: Void?) {
                super.onPostExecute(result)
                Thread.sleep(10000)
                startSyncFolders().execute()
            }
        }
    }

    private fun getSyncDataForClient(ftpClientId: Int, syncDataList: Array<SyncData>): List<SyncData> {
        return syncDataList.filter { syncData -> syncData.serverId == ftpClientId }
    }

    private fun remoteFileExists(localFile: File, remoteFiles: Array<FTPFile>): Boolean {
        remoteFiles.forEach { remoteFile ->
            if (remoteFile.name == localFile.name) {
                return true
            }
        }
        return false
    }

    private fun getNotification(title: String, content: String): Notification {
        val intent = Intent(this, FtpConnectionsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel()
                } else {
                    // If earlier version channel ID is not used
                    // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                    ""
                }
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        return notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .build()
    }
}
