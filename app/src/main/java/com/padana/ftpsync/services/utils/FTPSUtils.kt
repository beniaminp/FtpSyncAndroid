package com.padana.ftpsync.services.utils

import android.os.AsyncTask
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.SFTPException
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object FTPSUtils {
    private var ftpsClients: Map<Int, FTPSClient> = HashMap()

    fun createFTPSConenction(ftpClient: FtpClient): FTPSClient? {
        var ftpsClient: FTPSClient
        if (ftpsClients[ftpClient.id] == null) {
            ftpsClient = FTPSClient()
            ftpsClient.connect(ftpClient.server, ftpClient.sftpPort!!.toInt())
            ftpsClient.login(ftpClient.user, ftpClient.password)
        } else {
            ftpsClient = ftpsClients[ftpClient.id]!!
        }
        return ftpsClient
    }

    fun makeDirectory(sftp: FTPSClient, dirPath: String): Void {
        return object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                try {
                    sftp.makeDirectory(dirPath)
                } catch (e: Exception) {
                    LogerFileUtils.error(e.message!!)
                    e.printStackTrace()
                }
                return null
            }
        }.execute().get()
    }

    suspend fun listFiles(sftp: FTPSClient, syncData: SyncData): Array<FTPFile> {
        return withContext(Dispatchers.IO) {
            return@withContext sftp.listFiles(syncData.serverPath)
        }
    }

    fun listFilesByPath(sftp: FTPSClient, path: String): Array<FTPFile> {
        return object : AsyncTask<Void, Void, Array<FTPFile>>() {
            override fun doInBackground(vararg voids: Void): Array<FTPFile> {
                return sftp.listFiles(path)
            }
        }.execute().get()
    }

    suspend fun checkDirectoryExists(sftp: FTPSClient, dirPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (sftp.stat(dirPath) != null) {
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

    suspend fun remoteFileExists(localFile: File, remoteFiles: Array<FTPFile>): Boolean {
        return withContext(Dispatchers.IO) {
            remoteFiles.forEach { remoteFile ->
                if (remoteFile.name == localFile.name) {
                    return@withContext true
                }
            }
            return@withContext false
        }

    }

    suspend fun storeFileOnRemote(localFile: File, sftp: FTPSClient, syncData: SyncData): Boolean? {
        return withContext(Dispatchers.IO) {
            try {
                sftp.storeFile(syncData.serverPath + "/" + localFile.name, FileInputStream(localFile))
            } catch (e: SFTPException) {
                LogerFileUtils.error(e.message!! + " => " + localFile.name)
                return@withContext false
            }
            return@withContext true
        }
    }

    suspend fun storeFileOnRemoteSimple(localFileIS: InputStream, sftp: FTPSClient, location: String, fileName: String): Boolean? {
        return withContext(Dispatchers.IO) {
            try {
                sftp.storeFile("$location/$fileName", localFileIS)
            } catch (e: SFTPException) {
                LogerFileUtils.error(e.message!! + " => " + fileName)
                return@withContext false
            }
            return@withContext true
        }
    }

    suspend fun makeDirectories(sftp: FTPSClient, dirPath: String): Boolean {
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
                    val existed: Boolean = checkDirectoryExists(sftp, partialPath)
                    if (!existed) {
                        sftp.makeDirectory(partialPath)
                    }
                    partialPath += "/"
                }
            }
            return@withContext true
        }
    }
}