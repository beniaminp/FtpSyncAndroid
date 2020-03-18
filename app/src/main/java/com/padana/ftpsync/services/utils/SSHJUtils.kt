package com.padana.ftpsync.services.utils

import android.os.AsyncTask
import com.padana.ftpsync.MyApp
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.FileSystemFile
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.*
import java.security.Security


object SSHJUtils {
    private var sftpClients: Map<Int, SFTPClient> = HashMap()
    fun createSFTPConnection(ftpClient: FtpClient): SFTPClient? {
        var sftpClient: SFTPClient
        if (sftpClients[ftpClient.id] == null) {
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), Security.getProviders().size + 1)
            val ssh = SSHClient()
            ssh.useCompression()
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connect(ftpClient.server, if (ftpClient.sftpPort != null) ftpClient.sftpPort!!.toInt() else 22)
            ssh.authPassword(ftpClient.user, ftpClient.password)
            sftpClient = ssh.newSFTPClient()
        } else {
            sftpClient = sftpClients[ftpClient.id]!!
        }
        return sftpClient
    }

    fun makeDirectory(sftp: SFTPClient, dirPath: String): Void {
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

    suspend fun listFiles(sftp: SFTPClient, syncData: SyncData): List<RemoteResourceInfo> {
        return withContext(Dispatchers.IO) {
            return@withContext sftp.ls(syncData.serverPath)
        }
    }

    fun listFilesByPath(sftp: SFTPClient, path: String): List<RemoteResourceInfo> {
        return object : AsyncTask<Void, Void, List<RemoteResourceInfo>>() {
            override fun doInBackground(vararg voids: Void): List<RemoteResourceInfo> {
                return sftp.ls(path)
            }
        }.execute().get()
    }

    suspend fun checkDirectoryExists(sftp: SFTPClient, dirPath: String): Boolean {
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

    suspend fun remoteFileExists(localFile: File, remoteFiles: List<RemoteResourceInfo>): Boolean {
        return withContext(Dispatchers.IO) {
            remoteFiles.forEach { remoteFile ->
                if (remoteFile.name == localFile.name) {
                    return@withContext true
                }
            }
            return@withContext false
        }

    }

    suspend fun storeFileOnRemote(localFile: File, sftp: SFTPClient, syncData: SyncData): Boolean? {
        return withContext(Dispatchers.IO) {
            val bis = BufferedInputStream(FileInputStream(localFile))
            try {
                sftp.put(localFile.absolutePath, syncData.serverPath + "/" + localFile.name)
            } catch (e: SFTPException) {
                LogerFileUtils.error(e.message!! + " => " + localFile.name)
                return@withContext false
            }
            bis.close()
            return@withContext true
        }
    }

    suspend fun storeFileOnRemoteSimple(localFileIS: InputStream, sftp: SFTPClient, location: String, fileName: String): Boolean? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File.createTempFile("tmp-file", "tmp", MyApp.getCtx().externalCacheDir)
                copyInputStreamToFile(localFileIS, file)
                sftp.put(FileSystemFile(file), "$location/$fileName")
                file.delete()
            } catch (e: SFTPException) {
                LogerFileUtils.error(e.message!! + " => " + fileName)
                return@withContext false
            }
            return@withContext true
        }
    }

    suspend fun makeDirectories(sftp: SFTPClient, dirPath: String): Boolean {
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

    @Throws(IOException::class)
    private fun copyInputStreamToFile(inputStream: InputStream, file: File) =
            FileOutputStream(file).use { outputStream ->
                var read: Int
                val bytes = ByteArray(1024)
                while (inputStream.read(bytes).also { read = it } != -1) {
                    outputStream.write(bytes, 0, read)
                }
            }
}