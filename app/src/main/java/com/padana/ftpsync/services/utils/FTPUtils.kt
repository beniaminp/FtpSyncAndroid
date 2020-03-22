package com.padana.ftpsync.services.utils

import android.os.AsyncTask
import android.os.Build
import com.padana.ftpsync.MyApp
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.common.IOUtils
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.StandardCopyOption


object FTPUtils : ConnectorUtil {
    private var apacheFtpClientsList: HashMap<Int, FTPClient> = HashMap()

    override fun createConnection(ftpClient: FtpClient): FTPClient {
        val apacheFtpClient = FTPClient()
        apacheFtpClient.connect(ftpClient.server, if (ftpClient.sftpPort != null) ftpClient.sftpPort!!.toInt() else 21)
        apacheFtpClient.login(ftpClient.user, ftpClient.password)
        apacheFtpClient.setFileType(FTP.BINARY_FILE_TYPE)
        apacheFtpClient.enterLocalPassiveMode()
        //  apacheFtpClient.sendCommand("OPTS UTF8 ON")
        // apacheFtpClient.connectTimeout = 5000
        // apacheFtpClientsList[ftpClient.id!!] = apacheFtpClient

        /* if (apacheFtpClientsList[ftpClient.id] == null) {
             apacheFtpClient = FTPClient()
             apacheFtpClient.connect(ftpClient.server, if (ftpClient.sftpPort != null) ftpClient.sftpPort!!.toInt() else 21)
             apacheFtpClient.login(ftpClient.user, ftpClient.password)
             apacheFtpClient.setFileType(FTP.BINARY_FILE_TYPE)
             apacheFtpClient.enterLocalPassiveMode()
             apacheFtpClient.sendCommand("OPTS UTF8 ON")
             apacheFtpClient.connectTimeout = 5000
             apacheFtpClientsList[ftpClient.id!!] = apacheFtpClient
         } else {
             apacheFtpClient = apacheFtpClientsList[ftpClient.id]!!
         }*/
        return apacheFtpClient
    }

    override fun makeDirectory(ftpClient: FtpClient, dirPath: String): Void {
        return object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                try {
                    createConnection(ftpClient).makeDirectory(dirPath)
                } catch (e: Exception) {
                    LogerFileUtils.error("makeDirectory -> " + e.message!!)
                    e.printStackTrace()
                }
                return null
            }
        }.execute().get()
    }

    override suspend fun listFiles(ftpClient: FtpClient, syncData: SyncData): List<ConnectorFile>? {
        return withContext(Dispatchers.IO) {
            return@withContext createConnection(ftpClient).listFiles(syncData.serverPath).toList().map { ftpFile -> ConnectorFile(ftpFile.name) }
        }
    }

    @Synchronized
    override fun listFilesByPath(ftpClient: FtpClient, path: String): List<ConnectorFile> {
        return object : AsyncTask<Void, Void, List<ConnectorFile>>() {
            override fun doInBackground(vararg voids: Void): List<ConnectorFile> {
                return createConnection(ftpClient).listFiles(path).toList().map { ftpFile -> ConnectorFile(ftpFile.name) }
            }
        }.execute().get()
    }

    override suspend fun checkDirectoryExists(ftpClient: FtpClient, dirPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (createConnection(ftpClient).changeWorkingDirectory(dirPath)) {
                    return@withContext true
                }
                return@withContext false
            } catch (e: Exception) {
                LogerFileUtils.error("checkDirectoryExists -> " + e.message!!)
                e.printStackTrace()

                return@withContext false
            }
        }
    }

    override suspend fun remoteFileExists(localFile: File, remoteFiles: List<Any>): Boolean {
        return withContext(Dispatchers.IO) {
            (remoteFiles as List<FTPFile>).forEach { remoteFile ->
                if (remoteFile.name == localFile.name) {
                    return@withContext true
                }
            }
            return@withContext false
        }

    }

    override suspend fun storeFileOnRemote(localFile: File, ftpClient: FtpClient, syncData: SyncData): Boolean? {
        return withContext(Dispatchers.IO) {
            val bis = BufferedInputStream(FileInputStream(localFile))
            try {
                createConnection(ftpClient).changeWorkingDirectory(syncData.serverPath)
                createConnection(ftpClient).storeFile(localFile.name, File(localFile.absolutePath).inputStream())
            } catch (e: Exception) {
                LogerFileUtils.error("storeFileOnRemote -> " + e.message!! + " => " + localFile.name)
                return@withContext false
            }
            bis.close()
            return@withContext true
        }
    }

    override suspend fun storeFileOnRemoteSimple(localFileIS: InputStream, ftpClient: FtpClient, location: String, fileName: String): Boolean? {
        return withContext(Dispatchers.IO) {
            createConnection(ftpClient).changeWorkingDirectory(location)
            val success = createConnection(ftpClient).storeFile(fileName, localFileIS)
            if (!success) {
                LogerFileUtils.error("Failed to transfer => $fileName")
            }
            return@withContext success
        }
    }

    override suspend fun makeDirectories(ftpClient: FtpClient, dirPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            createConnection(ftpClient).changeWorkingDirectory("/")

            val pathElements = dirPath.split("/").toTypedArray()
            var partialPath = ""

            if (pathElements != null && pathElements.isNotEmpty()) {
                for (singleDir in pathElements) {
                    if (singleDir == "") {
                        partialPath += "/"
                        continue
                    }
                    partialPath += singleDir
                    val existed: Boolean = checkDirectoryExists(ftpClient, singleDir)
                    if (!existed) {
                        createConnection(ftpClient).makeDirectory(singleDir)
                        createConnection(ftpClient).changeWorkingDirectory(singleDir)
                    }
                    partialPath += "/"
                }
            }
            createConnection(ftpClient).changeWorkingDirectory("/")
            return@withContext true
        }
    }

    override suspend fun getFile(ftpClient: FtpClient, fileLocation: String): File {
        val file = File.createTempFile("prefix", ".jpg", MyApp.getCtx().externalCacheDir)
        try {
            val inputStream: InputStream = createConnection(ftpClient).retrieveFileStream(fileLocation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.nio.file.Files.copy(
                        inputStream,
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING)
            }
            IOUtils.closeQuietly(inputStream)
        } catch (e: Exception) {
            LogerFileUtils.error("getFile -> " + e.message!!)
            e.printStackTrace()
        }
        return file
    }
}