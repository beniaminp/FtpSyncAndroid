package com.padana.ftpsync.services.utils

import android.os.AsyncTask
import org.apache.commons.net.ftp.FTPClient
import java.io.IOException

object FTPUtils {
    @Throws(IOException::class)
    fun makeDirectories(ftpClient: FTPClient, dirPath: String): Boolean {
        val pathElements = dirPath.split("/").toTypedArray()
        var partialPath = ""

        if (pathElements != null && pathElements.isNotEmpty()) {
            for (singleDir in pathElements) {
                if(singleDir== ""){
                    continue
                }
                partialPath += singleDir
                val existed: Boolean = checkDirectoryExists(ftpClient, partialPath)
                if (!existed) {
                    val created: Boolean = createDirectory(ftpClient, partialPath)
                    if (created) {
                        println("CREATED directory:"+ partialPath)
                        checkDirectoryExists(ftpClient, partialPath)
                    } else {
                        println("Reply code is "+ ftpClient.replyCode)
                        println("COULD NOT create directory: "+partialPath)
                        return false
                    }
                }
                partialPath += "/"
            }
        }
        return true
    }

    fun createDirectory(ftp: FTPClient, directorPath: String): Boolean {
        return object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg voids: Void): Boolean {
                return ftp.makeDirectory(directorPath)
            }
        }.execute().get()
    }

    fun checkDirectoryExists(ftp: FTPClient, directoryPath: String): Boolean {
        return object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg voids: Void): Boolean {
                return ftp.changeWorkingDirectory(directoryPath)
            }
        }.execute().get()
    }
}