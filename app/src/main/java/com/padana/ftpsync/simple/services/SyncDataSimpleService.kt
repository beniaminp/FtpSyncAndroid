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
import com.jcraft.jsch.ChannelSftp
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FileInfo
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import com.padana.ftpsync.services.utils.InternetUtils
import com.padana.ftpsync.services.utils.LogerFileUtils
import com.padana.ftpsync.services.utils.NotifUtils
import com.padana.ftpsync.services.utils.SFTPUtils
import com.padana.ftpsync.simple.services.utils.MediaUtils
import com.padana.ftpsync.utils.ConnTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPReply
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*


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
                    if (ftpClient.connectionType!!.toLowerCase() == ConnTypes.FTP) {
                        ftpClientConnectionsMap[ftpClient.id!!] = createFtpConnection(ftpClient)
                    } else if (ftpClient.connectionType!!.toLowerCase() == ConnTypes.SFTP) {
                        ftpClientConnectionsMap[ftpClient.id!!] = createSFTPConnection(ftpClient)
                    }
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
                var rootLocation = ""
                if (ftpClient.rootLocation.isNullOrEmpty()) {
                    rootLocation = "/home/" + ftpClient.user + "/FileSync/"
                } else {
                    rootLocation = ftpClient.rootLocation!! + "/FileSync/"
                }
                val connection = ftpClientConnectionsMap[ftpClient.id!!]

                connection?.let { client ->
                    val directoryExists = SFTPUtils.checkDirectoryExists(client as ChannelSftp, rootLocation)
                    if (!directoryExists) {
                        SFTPUtils.makeDirectories(client, rootLocation)
                    }
                    var fileInfos = getAllFileInfos(ftpClient)
                    val thumbnailDirectoryExists = SFTPUtils.checkDirectoryExists(client, "$rootLocation/thumbnails/")
                    if (!thumbnailDirectoryExists) {
                        SFTPUtils.makeDirectories(client, "$rootLocation/thumbnails/")
                    }

                    val remoteFiles: Vector<ChannelSftp.LsEntry>? = SFTPUtils.listFilesByPath(client, rootLocation) as Vector<ChannelSftp.LsEntry>?

                    val images = MediaUtils.getImages()?.take(10)
                    images?.forEach { image ->
                        var remoteFileExists = false
                        remoteFiles?.forEach { remoteFile ->
                            if (remoteFile.filename == image.name) {
                                remoteFileExists = true
                            }
                        }
                        var fileInfo: FileInfo? = fileInfos.find { fileInfo -> fileInfo.name == image.name }
                        if (!remoteFileExists) {
                            startForeground(NOTIFICATION_ID, NotifUtils.getNotification("File Sync", "Sending image ${image.name}..."))
                            val fileInputStream = contentResolver.openInputStream(image.uri)!!
                            SFTPUtils.storeFileOnRemoteSimple(fileInputStream, client, rootLocation, image.name)
                            fileInputStream.close()

                            val thumbnailOutputStream = ByteArrayOutputStream()
                            MediaUtils.getImageThumbnail(image).compress(Bitmap.CompressFormat.JPEG, 100, thumbnailOutputStream)
                            SFTPUtils.storeFileOnRemoteSimple(ByteArrayInputStream(thumbnailOutputStream.toByteArray()), client, "$rootLocation/thumbnails/", image.name + "_" + image.dateTaken)
                        }
                        if (fileInfo == null) {
                            fileInfo = FileInfo(null, ftpClient.id, image.name, image.dateTaken, "$rootLocation/thumbnails/${image.name}_${image.dateTaken}", "$rootLocation/${image.name}")
                            DatabaseClient(applicationContext).getAppDatabase().genericDAO.addFileInfo(fileInfo)
                        }
                    }
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
