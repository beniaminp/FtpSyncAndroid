/*
package com.padana.ftpsync.services

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import com.jcraft.jsch.ChannelSftp
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import com.padana.ftpsync.services.utils.*
import com.padana.ftpsync.utils.ConnTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                GlobalScope.launch {
                    loadRemoteClients()
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    doSync()
                }
            }
        }).start()
    }

    private suspend fun loadRemoteClients() {
        withContext(Dispatchers.IO) {
            ftpClientsList = loadAllFtpClients()!!
            ftpClientsList.forEach { ftpClient ->
                if (ftpClientConnectionsMap[ftpClient.id] == null) {
                    if (ftpClient.connectionType!!.toLowerCase().equals(ConnTypes.FTP)) {
                        ftpClientConnectionsMap[ftpClient.id!!] = createFtpConnection(ftpClient)
                    } else if (ftpClient.connectionType!!.toLowerCase().equals(ConnTypes.SFTP)) {
                        ftpClientConnectionsMap[ftpClient.id!!] = createSFTPConnection(ftpClient)
                    }
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

    private suspend fun doSync() {
        withContext(Dispatchers.IO) {
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
                                    ?: run { ftpClientConnectionsMap[ftpClient.id!!] = createFtpConnection(ftpClient) }
                        } else if (ftpClient.connectionType?.toLowerCase(Locale.ROOT).equals(ConnTypes.SFTP)) {
                            ftp?.let { client ->
                                storeFilesList.add(computeFilesSFTP(client, syncData))
                            }
                                    ?: run { ftpClientConnectionsMap[ftpClient.id!!] = createSFTPConnection(ftpClient) }
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

            doSync()
        }
    }

    private suspend fun sendSftpFilesToServer(storeFilesList: MutableList<MutableList<StoreFiles>>, ftpClient: FtpClient) {
        withContext(Dispatchers.IO) {
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
    }

    private fun computeFilesFTP(ftp: Any?, syncData: SyncData) {
        GlobalScope.launch {
            val directoryExists = checkDirectoryExists(ftp as FTPClient, syncData)
            if (!directoryExists) {
                FTPUtils.makeDirectories(ftp, syncData.serverPath!!)
            }

            val remoteFiles: Array<FTPFile> = listRemoteFiles(ftp, syncData)
            if (syncData.localPath == null) {
                throw RuntimeException("Path is null!")
            }
            val localFiles: Array<File> = File(syncData.localPath).listFiles()
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
    }

    private suspend fun computeFilesSFTP(sftp: Any?, syncData: SyncData): MutableList<StoreFiles> {
        return withContext(Dispatchers.IO) {
            val directoryExists = SFTPUtils.checkDirectoryExists(sftp as ChannelSftp, syncData.serverPath!!)
            if (!directoryExists) {
                SFTPUtils.makeDirectories(sftp, syncData.serverPath!!)
            }

            val remoteFiles: Vector<*>? = SFTPUtils.listFiles(sftp, syncData)
            if (syncData.localPath == null) {
                throw RuntimeException("Path is null!")
            }

            val storeFilesList: MutableList<StoreFiles> = mutableListOf()

            val localFiles: Array<File>? = File(syncData.localPath).listFiles()
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
            */
/*storeFilesList.forEach { storeFile ->
            SFTPUtils.storeFileOnRemote(storeFile.localFile, sftp, storeFile.syncData)
        }*//*

            return@withContext storeFilesList
        }
    }

    private suspend fun listRemoteFiles(ftp: FTPClient, syncData: SyncData): Array<FTPFile> {
        return withContext(Dispatchers.IO) {
            return@withContext ftp.listFiles(syncData.serverPath)
        }
    }

    private suspend fun makeDirectory(ftp: FTPClient, syncData: SyncData): Boolean {
        return withContext(Dispatchers.IO) {
            return@withContext ftp.makeDirectory(syncData.serverPath)
        }
    }

    private suspend fun storeFileOnRemote(localFile: File, ftp: FTPClient, syncData: SyncData) {
        withContext(Dispatchers.IO) {
            val bis = BufferedInputStream(FileInputStream(localFile))
            ftp.storeFile(syncData.serverPath + "/" + localFile.name, bis)
            bis.close()
        }
    }

    private suspend fun checkDirectoryExists(ftp: FTPClient, syncData: SyncData): Boolean {
        return withContext(Dispatchers.IO) {
            return@withContext ftp.changeWorkingDirectory(syncData.serverPath)
        }
    }

    private suspend fun loadAllSyncData(): Array<SyncData>? {
        return withContext(Dispatchers.IO) {
            return@withContext DatabaseClient(applicationContext).getAppDatabase().genericDAO.loadAllSyncData()
        }
    }

    private suspend fun loadAllFtpClients(): Array<FtpClient>? {
        return withContext(Dispatchers.IO) {
            return@withContext DatabaseClient(applicationContext).getAppDatabase().genericDAO.loadAllFtpClients()
        }
    }

    private suspend fun createFtpConnection(ftpClient: FtpClient): FTPClient? {
        return withContext(Dispatchers.IO) {
            val ftp = FTPClient()
            try {
                val config = FTPClientConfig()
                ftp.defaultTimeout = 5000
                ftp.configure(config)

                ftp.connect(ftpClient.server)

                val reply = ftp.replyCode

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

            return@withContext ftp
        }
    }

    private suspend fun createSFTPConnection(ftpClient: FtpClient): ChannelSftp? {
        return withContext(Dispatchers.IO) {
            try {
                return@withContext SFTPUtils.createSFTPConnection(ftpClient)
            } catch (e: Exception) {
                e.printStackTrace()

                LogerFileUtils.error(e.message!!)

                startForeground(NOTIFICATION_ID, NotifUtils.getNotification("Conenction error...", "Server " + ftpClient.hostName + " " + e.message!!))
                System.err.println("Could not connect to server...")
                return@withContext null
            }
        }
    }

    private fun getSyncDataForClient(ftpClientId: Int, syncDataList: Array<SyncData>): List<SyncData> {
        return syncDataList.filter { syncData -> syncData.serverId == ftpClientId }
    }

    private suspend fun remoteFileExists(localFile: File, remoteFiles: Array<FTPFile>): Boolean {
        return withContext(Dispatchers.IO) {
            remoteFiles.forEach { remoteFile ->
                if (remoteFile.name == localFile.name) {
                    return@withContext true
                }
            }
            return@withContext false
        }

    }

    class StoreFiles {
        lateinit var localFile: File
        lateinit var syncData: SyncData
    }
}
*/
