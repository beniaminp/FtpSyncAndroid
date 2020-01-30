package com.padana.ftpsync.services.utils

import android.os.AsyncTask
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.*

object SFTPUtils {
    fun createSFTPConnection(ftpClient: FtpClient): ChannelSftp? {
        var sftpChannel: ChannelSftp
        val jsch = JSch()

        val config = Properties()
        config["StrictHostKeyChecking"] = "no"

        val session: Session = jsch.getSession(ftpClient.user, ftpClient.server)
        session.setPassword(ftpClient.password)
        session.timeout = 30000
        session.setConfig(config)
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

    fun listFiles(sftp: ChannelSftp, syncData: SyncData): Vector<*>? {
        return object : AsyncTask<Void, Void, Vector<*>?>() {
            override fun doInBackground(vararg voids: Void): Vector<*>? {
                return sftp.ls(syncData.serverPath)
            }
        }.execute().get()
    }

    fun listFilesByPath(sftp: ChannelSftp, path: String): Vector<*>? {
        return object : AsyncTask<Void, Void, Vector<*>?>() {
            override fun doInBackground(vararg voids: Void): Vector<*>? {
                return sftp.ls(path)
            }
        }.execute().get()
    }

    fun checkDirectoryExists(sftp: ChannelSftp, dirPath: String): Boolean {
        return object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg voids: Void): Boolean {
                try {
                    if (sftp.stat(dirPath) != null) {
                        return true
                    }
                    return false
                } catch (e: Exception) {
                    LogerFileUtils.error(e.message!!)
                    e.printStackTrace()

                    return false
                }
            }
        }.execute().get()
    }

    fun remoteFileExists(localFile: File, remoteFiles: Vector<ChannelSftp.LsEntry>): Boolean {
        return object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg voids: Void): Boolean {
                remoteFiles.forEach { remoteFile ->
                    if (remoteFile.filename == localFile.name) {
                        return true
                    }
                }
                return false
            }
        }.execute().get()

    }

    fun storeFileOnRemote(localFile: File, sftp: ChannelSftp, syncData: SyncData): Boolean? {
        return object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg voids: Void?): Boolean? {
                val bis = BufferedInputStream(FileInputStream(localFile))
                try {
                    sftp.put(localFile.absolutePath, syncData.serverPath + "/" + localFile.name)
                } catch (e: SftpException) {
                    LogerFileUtils.error(e.message!! + " => " + localFile.name)
                    return false
                }
                bis.close()
                return true
            }
        }.execute().get()
    }

    fun makeDirectories(sftp: ChannelSftp, dirPath: String): Boolean {
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
        return true
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