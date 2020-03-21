package com.padana.ftpsync.services

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FileInfo
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import com.padana.ftpsync.services.utils.*
import com.padana.ftpsync.simple.services.utils.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import org.apache.commons.net.ftp.FTPClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File


class SyncDataSimpleService : Service() {
    lateinit var notificationManger: NotificationManager
    private val NOTIFICATION_ID = 102
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
                    val remoteConnector = RemoteConnector(ftpClient)
                    ftpClientConnectionsMap[ftpClient.id!!] = remoteConnector.createConnection()
                }
            }
            if (ftpClientsList.isEmpty()) {
                Thread.sleep(10000)
                loadRemoteClients()
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

        startForeground(NOTIFICATION_ID, NotifUtils.getNotification("File Sync", "Started in background..."))
    }

    private suspend fun doSync() {
        withContext(Dispatchers.IO) {
            ftpClientsList.forEach { ftpClient ->
                // start simple method
                val remoteConnector = RemoteConnector(ftpClient)
                var rootLocation = ""
                if (ftpClient.rootLocation.isNullOrEmpty()) {
                    rootLocation = "/FileSync"
                } else {
                    rootLocation = ftpClient.rootLocation!! + "/FileSync"
                }
                val connection = ftpClientConnectionsMap[ftpClient.id!!]

                connection?.let { client ->
                    processFiles(remoteConnector, rootLocation)
                }
                        ?: run { ftpClientConnectionsMap[ftpClient.id!!] = createSFTPConnection(ftpClient) }


                val videos = MediaUtils.getVideos()


                // end simple method
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

    private suspend fun processFiles(remoteConnector: RemoteConnector, rootLocation: String): Unit? {
        val directoryExists = remoteConnector.checkDirectoryExists(rootLocation)
        if (!directoryExists) {
            remoteConnector.makeDirectories(rootLocation)
        }
        val fileInfos = getAllFileInfos(remoteConnector.ftpClient)
        val thumbnailDirectoryExists = remoteConnector.checkDirectoryExists("$rootLocation/thumbnails/")
        if (!thumbnailDirectoryExists) {
            remoteConnector.makeDirectories("$rootLocation/thumbnails/")
        }

        val remoteFiles = remoteConnector.listFilesByPath(rootLocation)

        val images = MediaUtils.getImages()?.take(10)
        return images?.forEach { image ->
            var remoteFileExists = false
            (remoteFiles as List<RemoteResourceInfo>).forEach { remoteFile ->
                if (remoteFile.name == image.name) {
                    remoteFileExists = true
                }
            }
            var fileInfo: FileInfo? = fileInfos.find { fileInfo -> fileInfo.name == image.name }
            if (!remoteFileExists) {
                startForeground(NOTIFICATION_ID, NotifUtils.getNotification("File Sync", "Sending image ${image.name}..."))
                val fileInputStream = contentResolver.openInputStream(image.uri)!!
                remoteConnector.storeFileOnRemoteSimple(fileInputStream, rootLocation, image.name)
                fileInputStream.close()

                val thumbnailOutputStream = ByteArrayOutputStream()
                MediaUtils.getImageThumbnail(image).compress(Bitmap.CompressFormat.JPEG, 10, thumbnailOutputStream)
                remoteConnector.storeFileOnRemoteSimple(ByteArrayInputStream(thumbnailOutputStream.toByteArray()), "$rootLocation/thumbnails/", image.name)
            }
            if (fileInfo == null) {
                fileInfo = FileInfo(null, remoteConnector.ftpClient.id, image.name, image.dateTaken, "$rootLocation/thumbnails/${image.name}", "$rootLocation/${image.name}")
                DatabaseClient(applicationContext).getAppDatabase().genericDAO.addFileInfo(fileInfo)
            }
        }
    }

    private fun getAllFileInfos(ftpClient: FtpClient) =
            DatabaseClient(applicationContext).getAppDatabase().genericDAO.findAllFileInfoByServerId(ftpClient.id!!)

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
            try {
                return@withContext com.padana.ftpsync.services.utils.FTPUtils.createConnection(ftpClient)
            } catch (e: Exception) {
                e.printStackTrace()

                LogerFileUtils.error(e.message!!)

                startForeground(NOTIFICATION_ID, NotifUtils.getNotification("Connection error...", "Server " + ftpClient.hostName + " " + e.message!!))
                System.err.println("Could not connect to server...")
                return@withContext null
            }
        }
    }

    private suspend fun createSFTPConnection(ftpClient: FtpClient): SFTPClient? {
        return withContext(Dispatchers.IO) {
            try {
                return@withContext SSHJUtils.createConnection(ftpClient)
            } catch (e: Exception) {
                e.printStackTrace()

                LogerFileUtils.error(e.message!!)

                startForeground(NOTIFICATION_ID, NotifUtils.getNotification("Connection error...", "Server " + ftpClient.hostName + " " + e.message!!))
                System.err.println("Could not connect to server...")
                return@withContext null
            }
        }
    }


    class StoreFiles {
        lateinit var localFile: File
        lateinit var syncData: SyncData
    }
}
