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


object SSHJUtils : ConnectorUtil {
    private var sftpClients: HashMap<Int, SFTPClient> = HashMap()

    override fun createConnection(ftpClient: FtpClient): SFTPClient {
        val sftpClient: SFTPClient
        if (sftpClients[ftpClient.id] == null) {
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), Security.getProviders().size + 1)
            val ssh = SSHClient()
            // ssh.useCompression()
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connect(ftpClient.server, if (ftpClient.sftpPort != null) ftpClient.sftpPort!!.toInt() else 22)
            ssh.authPassword(ftpClient.user, ftpClient.password)
            sftpClient = ssh.newSFTPClient()
            sftpClients.put(ftpClient.id!!, sftpClient)
        } else {
            sftpClient = sftpClients[ftpClient.id]!!
        }
        return sftpClient
    }

    override fun makeDirectory(ftpClient: FtpClient, dirPath: String): Void {
        return object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                try {
                    createConnection(ftpClient).mkdir(dirPath)
                } catch (e: Exception) {
                    LogerFileUtils.error(e.message!!)
                    e.printStackTrace()
                }
                return null
            }
        }.execute().get()
    }

    override suspend fun listFiles(ftpClient: FtpClient, syncData: SyncData): List<Any> {
        return withContext(Dispatchers.IO) {
            return@withContext createConnection(ftpClient).ls(syncData.serverPath)
        }
    }

    override fun listFilesByPath(ftpClient: FtpClient, path: String): List<Any> {
        return object : AsyncTask<Void, Void, List<RemoteResourceInfo>>() {
            override fun doInBackground(vararg voids: Void): List<RemoteResourceInfo> {
                return createConnection(ftpClient).ls(path)
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
            (remoteFiles as List<RemoteResourceInfo>).forEach { remoteFile ->
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
                createConnection(ftpClient).put(localFile.absolutePath, syncData.serverPath + "/" + localFile.name)
            } catch (e: SFTPException) {
                LogerFileUtils.error(e.message!! + " => " + localFile.name)
                return@withContext false
            }
            bis.close()
            return@withContext true
        }
    }

    override suspend fun storeFileOnRemoteSimple(localFileIS: InputStream, ftpClient: FtpClient, location: String, fileName: String): Boolean? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File.createTempFile("tmp-file", "tmp", MyApp.getCtx().externalCacheDir)
                copyInputStreamToFile(localFileIS, file)
                createConnection(ftpClient).put(FileSystemFile(file), "$location/$fileName")
                file.delete()
            } catch (e: SFTPException) {
                LogerFileUtils.error(e.message!! + " => " + fileName)
                return@withContext false
            }
            return@withContext true
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
                        createConnection(ftpClient).mkdir(partialPath)
                    }
                    partialPath += "/"
                }
            }
            return@withContext true
        }
    }

    override suspend fun getFile(ftpClient: FtpClient, fileLocation: String): File {
        val file: File
        val outputFile = File.createTempFile("prefix", ".jpeg", MyApp.getCtx().externalCacheDir)
        createConnection(ftpClient).get(fileLocation, FileSystemFile(outputFile))
        file = outputFile
        return file
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