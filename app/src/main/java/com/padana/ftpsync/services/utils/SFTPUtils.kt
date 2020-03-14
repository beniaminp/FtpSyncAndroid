package com.padana.ftpsync.services.utils

import android.os.AsyncTask
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.*

object SFTPUtils {
    fun createSFTPConnection(ftpClient: FtpClient): ChannelSftp? {
        val sftpChannel: ChannelSftp
        val jsch = JSch()

        val config = Properties()
        config["StrictHostKeyChecking"] = "no"

        val session: Session = jsch.getSession(ftpClient.user, ftpClient.server)
        session.setPassword(ftpClient.password)
        session.timeout = 30000
        session.setConfig(config)
        session.port = if (ftpClient.sftpPort != null) ftpClient.sftpPort!!.toInt() else 22
        session.connect()

        sftpChannel = session.openChannel("sftp") as ChannelSftp
        sftpChannel.connect()
        return sftpChannel
    }

    fun makeDirectory(sftp: ChannelSftp, dirPath: String): Void {
        return object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                try {
                    sftp.mkdir(dirPath)
                } catch (e: Exception) {
                    LogerFileUtils.error(e.message!!)
                    e.printStackTrace()
                }
                return null
            }
        }.execute().get()
    }

    suspend fun listFiles(sftp: ChannelSftp, syncData: SyncData): Vector<*>? {
        return withContext(Dispatchers.IO) {
            return@withContext sftp.ls(syncData.serverPath)
        }
    }

    fun listFilesByPath(sftp: ChannelSftp, path: String): Vector<*>? {
        return object : AsyncTask<Void, Void, Vector<*>?>() {
            override fun doInBackground(vararg voids: Void): Vector<*>? {
                return sftp.ls(path)
            }
        }.execute().get()
    }

    suspend fun checkDirectoryExists(sftp: ChannelSftp, dirPath: String): Boolean {
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

    suspend fun remoteFileExists(localFile: File, remoteFiles: Vector<ChannelSftp.LsEntry>): Boolean {
        return withContext(Dispatchers.IO) {
            remoteFiles.forEach { remoteFile ->
                if (remoteFile.filename == localFile.name) {
                    return@withContext true
                }
            }
            return@withContext false
        }

    }

    suspend fun storeFileOnRemote(localFile: File, sftp: ChannelSftp, syncData: SyncData): Boolean? {
        return withContext(Dispatchers.IO) {
            val bis = BufferedInputStream(FileInputStream(localFile))
            try {
                sftp.put(localFile.absolutePath, syncData.serverPath + "/" + localFile.name)
            } catch (e: SftpException) {
                LogerFileUtils.error(e.message!! + " => " + localFile.name)
                return@withContext false
            }
            bis.close()
            return@withContext true
        }
    }

    suspend fun storeFileOnRemoteSimple(localFileIS: InputStream, sftp: ChannelSftp, location: String, fileName: String): Boolean? {
        return withContext(Dispatchers.IO) {
            try {
                sftp.put(localFileIS, "$location/$fileName")
            } catch (e: SftpException) {
                LogerFileUtils.error(e.message!! + " => " + fileName)
                return@withContext false
            }
            return@withContext true
        }
    }

    suspend fun makeDirectories(sftp: ChannelSftp, dirPath: String): Boolean {
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
                        sftp.mkdir(partialPath)
                    }
                    partialPath += "/"
                }
            }
            return@withContext true
        }
    }

/*    class SFTPProgressMonitor : SftpProgressMonitor {
        override fun count(count: Long): Boolean {


        }

        override fun end() {

        }

        override fun init(op: Int, src: String?, dest: String?, max: Long) {
        }

    }*/
}