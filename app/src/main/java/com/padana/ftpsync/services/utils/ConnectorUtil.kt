package com.padana.ftpsync.services.utils

import com.padana.ftpsync.entities.FileInfo
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import java.io.File
import java.io.InputStream

interface ConnectorUtil {
    fun createConnection(ftpClient: FtpClient): Any

    fun makeDirectory(ftpClient: FtpClient, dirPath: String): Void

    suspend fun listFiles(ftpClient: FtpClient, syncData: SyncData): List<Any>?

    fun listFilesByPath(ftpClient: FtpClient, path: String): List<Any>

    suspend fun checkDirectoryExists(ftpClient: FtpClient, dirPath: String): Boolean

    suspend fun remoteFileExists(localFile: File, remoteFiles: List<Any>): Boolean

    suspend fun storeFileOnRemote(localFile: File, ftpClient: FtpClient, syncData: SyncData): Boolean?

    suspend fun storeFileOnRemoteSimple(localFileIS: InputStream, ftpClient: FtpClient, location: String, fileName: String): Boolean?

    suspend fun makeDirectories(ftpClient: FtpClient, dirPath: String): Boolean

    suspend fun getFile(ftpClient: FtpClient, fileLocation: String): File
}