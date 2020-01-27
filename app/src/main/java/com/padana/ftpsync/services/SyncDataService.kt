package com.padana.ftpsync.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MAX
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.padana.ftpsync.R
import com.padana.ftpsync.activities.ftp_connections.FtpConnectionsActivity
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import com.padana.ftpsync.services.utils.FTPUtils
import com.padana.ftpsync.services.utils.SFTPUtils
import com.padana.ftpsync.utils.ConnTypes
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.*


class SyncDataService : Service() {
    lateinit var notificationManger: NotificationManager
    private val NOTIFICATION_ID = 101
    private val TIMES_TO_RETRY = 10
    private val TIME_TO_WAIT = 60
    private var NO_OF_RETRIES = 0
    lateinit var ftpClientsList: Array<FtpClient>
    private var ftpClientConnectionsMap: MutableMap<Int, Any> = mutableMapOf()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground()
        loadFtpClients()
        Thread(Runnable {
            run {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                doSync()
            }
        }).start()
    }

    private fun loadFtpClients() {
        ftpClientsList = loadAllFtpClients()!!
        ftpClientsList.forEach { ftpClient ->
            if (ftpClientConnectionsMap[ftpClient.id] == null) {
                if (ftpClient.connectionType!!.toLowerCase().equals(ConnTypes.FTP)) {
                    ftpClientConnectionsMap[ftpClient.id!!] = createFtpConnection(ftpClient)!!
                } else if (ftpClient.connectionType!!.toLowerCase().equals(ConnTypes.SFTP)) {
                    this.ftpClientConnectionsMap[ftpClient.id!!] = createSFTPConnection(ftpClient)!!
                }
            }
        }
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

    private fun doSync() {
        var syncDataList = loadAllSyncData()!!
        ftpClientsList.forEach { ftpClient ->
            val syncDataForClient: List<SyncData> = getSyncDataForClient(ftpClient.id!!, syncDataList)
            var storeFilesList: MutableList<MutableList<StoreFiles>> = mutableListOf()

            try {
                var ftp = ftpClientConnectionsMap[ftpClient.id!!]!!
                syncDataForClient.forEach { syncData ->
                    if (ftpClient.connectionType!!.toLowerCase().equals(ConnTypes.FTP)) {
                        computeFilesFTP(ftp, syncData)
                    } else if (ftpClient.connectionType!!.toLowerCase().equals(ConnTypes.SFTP)) {
                        storeFilesList.add(computeFilesSFTP(ftp, syncData))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                startForeground(NOTIFICATION_ID, getNotification("ERROR", e.message.toString()))
                System.err.println("Could not connect to server...")
            }
            if (ftpClient.connectionType!!.toLowerCase().equals(ConnTypes.SFTP)) {
                var count = 0
                storeFilesList.forEach { storeList -> count += storeList.size }
                if (count > 0) {
                    startForeground(NOTIFICATION_ID,
                            getNotification("Found Files", "Found " + count + " files for " + ftpClient.hostName + ". Start sync..."))
                    for ((index, storeList) in storeFilesList.withIndex()) {
                        storeList.forEach { storeFile ->
                            SFTPUtils.storeFileOnRemote(storeFile.localFile, (ftpClientConnectionsMap[ftpClient.id!!] as ChannelSftp?)!!, storeFile.syncData)
                            // count -= 1
                            startForeground(NOTIFICATION_ID,
                                    getNotification("Sending files...", index.toString() + " of " + storeList.size + " files for " + ftpClient.hostName))
                        }
                    }
                }

                startForeground(NOTIFICATION_ID,
                        getNotification("Waiting...", "Waiting for files..."))
            }
        }

        if (NO_OF_RETRIES == TIMES_TO_RETRY) {
            Thread.sleep(TIME_TO_WAIT * 60000L)
            NO_OF_RETRIES = 0
        }
        Thread.sleep(10000)
        NO_OF_RETRIES = NO_OF_RETRIES++

        this.doSync()
    }

    private fun computeFilesFTP(ftp: Any, syncData: SyncData) {
        var directoryExists = checkDirectoryExists(ftp as FTPClient, syncData)
        if (!directoryExists) {
            FTPUtils.makeDirectories(ftp, syncData.serverPath!!)
        }
        var remoteFiles: Array<FTPFile> = listRemoteFiles(ftp, syncData)
        if (syncData.localPath == null) {
            throw RuntimeException("Path is null!")
        }
        var localFiles: Array<File> = File(syncData.localPath).listFiles()
        if (remoteFiles.size != localFiles.size) {
            localFiles.forEach { localFile ->
                if (!remoteFileExists(localFile, remoteFiles)) {
                    if (!localFile.isDirectory) {
                        storeFileOnRemote(localFile, ftp, syncData)
                    } else {

                    }
                }
            }
        }
    }

    private fun computeFilesSFTP(sftp: Any, syncData: SyncData): MutableList<StoreFiles> {
        var directoryExists = SFTPUtils.checkDirectoryExists(sftp as ChannelSftp, syncData.serverPath!!)
        if (!directoryExists) {
            SFTPUtils.makeDirectories(sftp, syncData.serverPath!!)
        }
        // sftp.cd(syncData.serverPath)

        var remoteFiles: Vector<*>? = SFTPUtils.listFiles(sftp, syncData)
        if (syncData.localPath == null) {
            throw RuntimeException("Path is null!")
        }

        val storeFilesList: MutableList<StoreFiles> = mutableListOf()

        var localFiles: Array<File>? = File(syncData.localPath).listFiles()
        if (localFiles != null && remoteFiles!!.size != localFiles.size) {
            localFiles.forEach { localFile ->
                if (!SFTPUtils.remoteFileExists(localFile, remoteFiles as Vector<ChannelSftp.LsEntry>)) {
                    if (!localFile.isDirectory) {
                        var storeFile = StoreFiles()
                        storeFile.localFile = localFile
                        storeFile.syncData = syncData
                        storeFilesList.add(storeFile)
                    } else {

                    }
                }
            }
        }
        /*storeFilesList.forEach { storeFile ->
            SFTPUtils.storeFileOnRemote(storeFile.localFile, sftp, storeFile.syncData)
        }*/
        return storeFilesList
    }

    private fun listRemoteFiles(ftp: FTPClient, syncData: SyncData): Array<FTPFile> =
            object : AsyncTask<Void, Void, Array<FTPFile>>() {
                override fun doInBackground(vararg voids: Void): Array<FTPFile> {
                    return ftp.listFiles(syncData.serverPath)
                }
            }.execute().get()

    private fun makeDirectory(ftp: FTPClient, syncData: SyncData): Boolean {
        return object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg voids: Void): Boolean {
                return ftp.makeDirectory(syncData.serverPath)
            }
        }.execute().get()
    }

    private fun storeFileOnRemote(localFile: File, ftp: FTPClient, syncData: SyncData): Void? {
        return object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void?): Void? {
                val bis = BufferedInputStream(FileInputStream(localFile))
                ftp.storeFile(syncData.serverPath + "/" + localFile.name, bis)
                bis.close()
                return null
            }
        }.execute().get()
    }

    private fun checkDirectoryExists(ftp: FTPClient, syncData: SyncData): Boolean {
        return object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg voids: Void): Boolean {
                return ftp.changeWorkingDirectory(syncData.serverPath)
            }
        }.execute().get()
    }

    private fun loadAllSyncData(): Array<SyncData>? {
        return object : AsyncTask<Void, Void, Array<SyncData>>() {
            override fun doInBackground(vararg voids: Void): Array<SyncData> {
                return DatabaseClient(applicationContext).getAppDatabase().genericDAO.loadAllSyncData()
            }
        }.execute().get()
    }

    private fun loadAllFtpClients(): Array<FtpClient>? {
        return object : AsyncTask<Void, Void, Array<FtpClient>>() {
            override fun doInBackground(vararg voids: Void): Array<FtpClient> {
                return DatabaseClient(applicationContext).getAppDatabase().genericDAO.loadAllFtpClients()
            }
        }.execute().get()
    }

    private fun createFtpConnection(ftpClient: FtpClient): FTPClient? {
        return object : AsyncTask<Void, Void, FTPClient>() {
            override fun doInBackground(vararg voids: Void): FTPClient {
                var ftp = FTPClient()
                try {
                    var config = FTPClientConfig()
                    ftp.defaultTimeout = 5000
                    ftp.configure(config)

                    ftp.connect(ftpClient!!.server)

                    var reply = ftp.replyCode

                    if (!FTPReply.isPositiveCompletion(reply)) {
                        ftp.disconnect()
                    }
                    ftp.autodetectUTF8 = true
                    ftp.controlEncoding = "UTF-8"

                    ftp.login(ftpClient.user, ftpClient.password)
                    ftp.enterLocalPassiveMode()
                } catch (e: Exception) {
                    e.printStackTrace()
                    startForeground(NOTIFICATION_ID, getNotification("ERROR", e.message.toString()))
                    System.err.println("Could not connect to server...")
                }

                return ftp
            }
        }.execute().get()
    }

    private fun createSFTPConnection(ftpClient: FtpClient): ChannelSftp? {
        return object : AsyncTask<Void, Void, ChannelSftp>() {
            override fun doInBackground(vararg voids: Void): ChannelSftp {
                var sftpChannel = ChannelSftp()
                try {
                    val jsch = JSch()

                    val config = Properties()
                    config["StrictHostKeyChecking"] = "no"

                    val session: Session = jsch.getSession(ftpClient.user, ftpClient.server)
                    session.setPassword(ftpClient.password)
                    session.setConfig(config)
                    session.connect()

                    sftpChannel = session.openChannel("sftp") as ChannelSftp
                    sftpChannel.connect()
                    return sftpChannel
                } catch (e: Exception) {
                    e.printStackTrace()
                    startForeground(NOTIFICATION_ID, getNotification("ERROR", e.message.toString()))
                    System.err.println("Could not connect to server...")
                }

                return sftpChannel
            }
        }.execute().get()
    }

    private fun getSyncDataForClient(ftpClientId: Int, syncDataList: Array<SyncData>): List<SyncData> {
        return syncDataList.filter { syncData -> syncData.serverId == ftpClientId }
    }

    private fun remoteFileExists(localFile: File, remoteFiles: Array<FTPFile>): Boolean {
        return object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg voids: Void): Boolean {
                remoteFiles.forEach { remoteFile ->
                    if (remoteFile.name == localFile.name) {
                        return true
                    }
                }
                return false
            }
        }.execute().get()

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

    class StoreFiles {
        lateinit var localFile: File
        lateinit var syncData: SyncData
    }
}
