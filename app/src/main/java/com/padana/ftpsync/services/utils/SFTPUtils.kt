package com.padana.ftpsync.services.utils

import android.os.AsyncTask
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpProgressMonitor
import com.padana.ftpsync.entities.SyncData
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.*

object SFTPUtils {
    fun makeDirectory(sftp: ChannelSftp, dirPath: String): Void {
        return object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                try {
                    sftp.mkdir(dirPath)
                } catch (e: Exception) {
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

    fun checkDirectoryExists(sftp: ChannelSftp, dirPath: String): Boolean {
        return object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg voids: Void): Boolean {
                try {
                    if (sftp.stat(dirPath) != null) {
                        return true
                    }
                    return false
                } catch (e: Exception) {
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

    fun storeFileOnRemote(localFile: File, sftp: ChannelSftp, syncData: SyncData): Void? {
        return object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void?): Void? {
                val bis = BufferedInputStream(FileInputStream(localFile))
                sftp.put(localFile.absolutePath, syncData.serverPath + "/" + localFile.name)
                bis.close()
                return null
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