package com.padana.ftpsync.services.utils

import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import java.io.File
import java.io.InputStream

class RemoteConnector(val ftpClient: FtpClient) {

    fun createConnection(): Any {
        if (ftpClient.connectionType == "SFTP") {
            return SSHJUtils.createConnection(ftpClient)
        } else if (ftpClient.connectionType == "FTP") {
            return FTPUtils.createConnection(ftpClient)
        }
        throw RuntimeException("No Connector Client Defined")
    }

    suspend fun makeDirectory(dirPath: String) {
        if (ftpClient.connectionType == "SFTP") {
            SSHJUtils.makeDirectories(ftpClient, dirPath)
        } else if (ftpClient.connectionType == "FTP") {
            FTPUtils.makeDirectories(ftpClient, dirPath)
        }
        throw RuntimeException("No Connector Client Defined")
    }

    suspend fun listFiles(syncData: SyncData): List<Any>? {
        if (ftpClient.connectionType == "SFTP") {
            return SSHJUtils.listFiles(ftpClient, syncData)
        } else if (ftpClient.connectionType == "FTP") {
            return FTPUtils.listFiles(ftpClient, syncData)
        }
        throw RuntimeException("No Connector Client Defined")
    }

    fun listFilesByPath(path: String): List<ConnectorFile> {
        if (ftpClient.connectionType == "SFTP") {
            return SSHJUtils.listFilesByPath(ftpClient, path)
        } else if (ftpClient.connectionType == "FTP") {
            return FTPUtils.listFilesByPath(ftpClient, path)
        }
        throw RuntimeException("No Connector Client Defined")
    }

    suspend fun checkDirectoryExists(dirPath: String): Boolean {
        if (ftpClient.connectionType == "SFTP") {
            return SSHJUtils.checkDirectoryExists(ftpClient, dirPath)
        } else if (ftpClient.connectionType == "FTP") {
            return FTPUtils.checkDirectoryExists(ftpClient, dirPath)
        }
        throw RuntimeException("No Connector Client Defined")
    }

    suspend fun remoteFileExists(localFile: File, remoteFiles: List<Any>): Boolean {
        if (ftpClient.connectionType == "SFTP") {
            return SSHJUtils.remoteFileExists(localFile, remoteFiles)
        } else if (ftpClient.connectionType == "FTP") {
            return FTPUtils.remoteFileExists(localFile, remoteFiles)
        }
        throw RuntimeException("No Connector Client Defined")
    }

    suspend fun storeFileOnRemote(localFile: File, syncData: SyncData): Boolean? {
        if (ftpClient.connectionType == "SFTP") {
            return SSHJUtils.storeFileOnRemote(localFile, ftpClient, syncData)
        } else if (ftpClient.connectionType == "FTP") {
            return FTPUtils.storeFileOnRemote(localFile, ftpClient, syncData)
        }
        throw RuntimeException("No Connector Client Defined")
    }

    suspend fun storeFileOnRemoteSimple(localFileIS: InputStream, location: String, fileName: String): Boolean? {
        if (ftpClient.connectionType == "SFTP") {
            return SSHJUtils.storeFileOnRemoteSimple(localFileIS, ftpClient, location, fileName)
        } else if (ftpClient.connectionType == "FTP") {
            return FTPUtils.storeFileOnRemoteSimple(localFileIS, ftpClient, location, fileName)
        }
        throw RuntimeException("No Connector Client Defined")
    }

    suspend fun makeDirectories(dirPath: String): Boolean {
        if (ftpClient.connectionType == "SFTP") {
            return SSHJUtils.makeDirectories(ftpClient, dirPath)
        } else if (ftpClient.connectionType == "FTP") {
            return FTPUtils.makeDirectories(ftpClient, dirPath)
        }
        throw RuntimeException("No Connector Client Defined")
    }

    suspend fun getFile(fileLocation: String): File {
        if (ftpClient.connectionType == "SFTP") {
            return SSHJUtils.getFile(ftpClient, fileLocation)
        } else if (ftpClient.connectionType == "FTP") {
            return FTPUtils.getFile(ftpClient, fileLocation)
        }
        throw RuntimeException("No Connector Client Defined")
    }
}