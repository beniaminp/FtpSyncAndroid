package com.padana.ftpsync.services

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import com.jcraft.jsch.ChannelSftp
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import com.padana.ftpsync.services.utils.*
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
    private var ftpClientConnectionsMap: MutableMap<Int, Any?> = mutableMapOf()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground()

        Thread(Runnable {
            run {
                while (!InternetUtils.isInternetAvailable()) {
                    startForeground(NOTIFICATION_ID,
                            NotifUtils.getNotification("No internet...", "Please connect to a network and retry"))
                    LogerFileUtils.error("No internet... Please connect to a network and retry")
                    Thread.sleep(TIME_TO_WAIT * 60L)
                }
                loadRemoteClients()
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                doSync()
            }
        }).start()
    }

    private fun loadRemoteClients() {
        ftpClientsList = loadAllFtpClients()!!
        ftpClientsList.forEach { ftpClient ->
            if (ftpClientConnectionsMap[ftpClient.id] == null) {
                if (ftpClient.connectionType!!.toLowerCase().equals(ConnTypes.FTP)) {
                    ftpClientConnectionsMap[ftpClient.id!!] = createFtpConnection(ftpClient)
                } else if (ftpClient.connectionType!!.toLowerCase().equals(ConnTypes.SFTP)) {
                    this.ftpClientConnectionsMap[ftpClient.id!!] = createSFTPConnection(ftpClient)
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

        startForeground(NOTIFICATION_ID, NotifUtils.getNotification("FTP Sync", "Started in background..."))
    }

    private fun doSync() {
        val syncDataList = loadAllSyncData()!!
        ftpClientsList.forEach { ftpClient ->
            val syncDataForClient: List<SyncData> = getSyncDataForClient(ftpClient.id!!, syncDataList)
            val storeFilesList: MutableList<MutableList<StoreFiles>> = mutableListOf()

            try {
                val ftp = ftpClientConnectionsMap[ftpClient.id!!]

                syncDataForClient.forEach { syncData ->
                    if (ftpClient.connectionType?.toLowerCase(Locale.ROOT).equals(ConnTypes.FTP)) {
                        ftp?.let { client ->
                            computeFilesFTP(client, syncData)
                        }
                                ?: run { this.ftpClientConnectionsMap[ftpClient.id!!] = createFtpConnection(ftpClient) }
                    } else if (ftpClient.connectionType?.toLowerCase(Locale.ROOT).equals(ConnTypes.SFTP)) {
                        ftp?.let { client ->
                            storeFilesList.add(computeFilesSFTP(client, syncData))
                        }
                                ?: run { this.ftpClientConnectionsMap[ftpClient.id!!] = createSFTPConnection(ftpClient) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                LogerFileUtils.error(e.message!!)
                startForeground(NOTIFICATION_ID, NotifUtils.getNotification("ERROR", e.message.toString()))
            }
            if (ftpClient.connectionType?.toLowerCase(Locale.ROOT) == ConnTypes.SFTP) {
                sendSftpFilesToServer(storeFilesList, ftpClient)
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

    private fun sendSftpFilesToServer(storeFilesList: MutableList<MutableList<StoreFiles>>, ftpClient: FtpClient) {
        var count = 0
        storeFilesList.forEach { storeList -> count += storeList.size }
        if (count > 0) {
            startForeground(NOTIFICATION_ID,
                    NotifUtils.getNotification("Found Files", "Found " + count + " files for " + ftpClient.hostName + ". Start sync..."))
            storeFilesList.forEach { storeList ->
                storeList.forEach { storeFile ->
                    val fileCopied: Boolean? = SFTPUtils.storeFileOnRemote(storeFile.localFile, (ftpClientConnectionsMap[ftpClient.id!!] as ChannelSftp?)!!, storeFile.syncData)
                    fileCopied?.let { isCopied ->
                        if (isCopied) {
                            count -= 1
                            startForeground(NOTIFICATION_ID,
                                    NotifUtils.getNotification("Sending files...", count.toString() + " of " + storeList.size + " files for " + ftpClient.hostName))
                            LogerFileUtils.info(storeFile.localFile.name + " sent to " + ftpClient.hostName)
                        } else {
                            startForeground(NOTIFICATION_ID,
                                    NotifUtils.getNotification("Error on file...", "Could not copy file: " + storeFile.localFile))
                            LogerFileUtils.error(storeFile.localFile.name + " could not be sent to " + ftpClient.hostName)
                        }
                    }
                }
            }
        }

        startForeground(NOTIFICATION_ID,
                NotifUtils.getNotification("Waiting...", "Waiting for files..."))
    }

    private fun computeFilesFTP(ftp: Any?, syncData: SyncData) {
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

    private fun computeFilesSFTP(sftp: Any?, syncData: SyncData): MutableList<StoreFiles> {
        var directoryExists = SFTPUtils.checkDirectoryExists(sftp as ChannelSftp, syncData.serverPath!!)
        if (!directoryExists) {
            SFTPUtils.makeDirectories(sftp, syncData.serverPath!!)
        }

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
                    LogerFileUtils.error(e.message!!)
                    startForeground(NOTIFICATION_ID, NotifUtils.getNotification("ERROR", e.message.toString()))
                    System.err.println("Could not connect to server...")
                }

                return ftp
            }
        }.execute().get()
    }

    private fun createSFTPConnection(ftpClient: FtpClient): ChannelSftp? {
        return object : AsyncTask<Void, Void, ChannelSftp?>() {
            override fun doInBackground(vararg voids: Void): ChannelSftp? {
                return try {
                    SFTPUtils.createSFTPConnection(ftpClient)
                } catch (e: Exception) {
                    e.printStackTrace()

                    LogerFileUtils.error(e.message!!)

                    startForeground(NOTIFICATION_ID, NotifUtils.getNotification("Conenction error...", "Server " + ftpClient.hostName + " " + e.message!!))
                    System.err.println("Could not connect to server...")
                    null
                }
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

    class StoreFiles {
        lateinit var localFile: File
        lateinit var syncData: SyncData
    }
}
