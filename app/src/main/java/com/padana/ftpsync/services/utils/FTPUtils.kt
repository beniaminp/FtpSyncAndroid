package com.padana.ftpsync.services.utils

import android.os.AsyncTask
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.*


object FTPUtils : ConnectorUtil {
    private var apacheFtpClientsList: HashMap<Int, FTPClient> = HashMap()

    override fun createConnection(ftpClient: FtpClient): FTPClient {
        val apacheFtpClient: FTPClient
        if (apacheFtpClientsList[ftpClient.id] == null) {
            apacheFtpClient = FTPClient()
            apacheFtpClient.connect(ftpClient.server, if (ftpClient.sftpPort != null) ftpClient.sftpPort!!.toInt() else 21)
            apacheFtpClient.login(ftpClient.user, ftpClient.password)
            apacheFtpClient.enterLocalPassiveMode()
            apacheFtpClient.setFileType(FTP.BINARY_FILE_TYPE)
            apacheFtpClientsList[ftpClient.id!!] = apacheFtpClient
        } else {
            apacheFtpClient = apacheFtpClientsList[ftpClient.id]!!
        }
        return apacheFtpClient
    }

    override fun makeDirectory(ftpClient: FtpClient, dirPath: String): Void {
        return object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                try {
                    createConnection(ftpClient).makeDirectory(dirPath)
                } catch (e: Exception) {
                    LogerFileUtils.error(e.message!!)
                    e.printStackTrace()
                }
                return null
            }
        }.execute().get()
    }

    override suspend fun listFiles(ftpClient: FtpClient, syncData: SyncData): List<Any>? {
        return withContext(Dispatchers.IO) {
            return@withContext createConnection(ftpClient).listFiles(syncData.serverPath).toList()
        }
    }

    override fun listFilesByPath(ftpClient: FtpClient, path: String): List<Any> {
        return object : AsyncTask<Void, Void, List<Any>>() {
            override fun doInBackground(vararg voids: Void): List<Any> {
                return createConnection(ftpClient).listFiles(path).toList()
            }
        }.execute().get()
    }

    override suspend fun checkDirectoryExists(ftpClient: FtpClient, dirPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (createConnection(ftpClient).stat(dirPath) != null) {
                    return@withContext true
                }
                return@withContext false
            } catch (e: Exception) {
                LogerFileUtils.error(e.message!!)
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
                createConnection(ftpClient).storeFile(syncData.serverPath + "/" + localFile.name, File(localFile.absolutePath).inputStream())
            } catch (e: Exception) {
                LogerFileUtils.error(e.message!! + " => " + localFile.name)
                return@withContext false
            }
            bis.close()
            return@withContext true
        }
    }

    override suspend fun storeFileOnRemoteSimple(localFileIS: InputStream, ftpClient: FtpClient, location: String, fileName: String): Boolean? {
        return withContext(Dispatchers.IO) {
            val success = createConnection(ftpClient).storeFile("$location/$fileName", localFileIS)
            if (!success) {
                LogerFileUtils.error("Failed to transfer => $fileName")
            }
            return@withContext success
        }
    }

    override suspend fun makeDirectories(ftpClient: FtpClient, dirPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val pathElements = dirPath.split("/").toTypedArray()
            var partialPath = ""

            if (pathElements != null && pathElements.isNotEmpty()) {
                for (singleDir in pathElements) {
                    if (singleDir == "") {
                        partialPath += "/"
                        continue
                    }
                    partialPath += singleDir
                    val existed: Boolean = checkDirectoryExists(ftpClient, partialPath)
                    if (!existed) {
                        createConnection(ftpClient).makeDirectory(partialPath)
                    }
                    partialPath += "/"
                }
            }
            return@withContext true
        }
    }

    override suspend fun getFile(ftpClient: FtpClient, fileLocation: String): File {
        val file = File("")
        val outputStream2: OutputStream = BufferedOutputStream(FileOutputStream(file))
        val inputStream: InputStream = createConnection(ftpClient).retrieveFileStream(fileLocation)
        val bytesArray = ByteArray(4096)
        var bytesRead = -1
        while (inputStream.read(bytesArray).also { bytesRead = it } != -1) {
            outputStream2.write(bytesArray, 0, bytesRead)
        }

        createConnection(ftpClient).completePendingCommand()
        outputStream2.close()
        inputStream.close()
        return file
    }
}