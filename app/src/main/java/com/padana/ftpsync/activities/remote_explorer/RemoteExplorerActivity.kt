package com.padana.ftpsync.activities.remote_explorer

import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import com.padana.ftpsync.R
import com.padana.ftpsync.activities.remote_explorer.adapters.RemoteListAdapter
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.folder_list.Folder
import com.padana.ftpsync.services.utils.LogerFileUtils
import com.padana.ftpsync.services.utils.SFTPUtils
import com.padana.ftpsync.utils.ConnTypes
import com.squareup.picasso.Picasso
import com.stfalcon.imageviewer.StfalconImageViewer
import kotlinx.android.synthetic.main.activity_remote_explorer.*
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList

class RemoteExplorerActivity : AppCompatActivity() {
    private lateinit var currentPath: String
    private lateinit var ftpClient: FtpClient
    private lateinit var adapter: RemoteListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_explorer)

        var bundle = intent.extras
        ftpClient = bundle?.getSerializable("ftpClient") as FtpClient
        currentPath = ftpClient.rootLocation + "/" + Build.MODEL
        currentFolderPathTV.text = currentPath

        adapter = RemoteListAdapter(this, R.layout.remote_list_row, getFileList(currentPath)!!.toMutableList())
        remote_list.adapter = adapter

        setOnClickListenerListAdapter()
    }

    private val IMAGE_EXTENSION = ".jpg"
    private val VIDEO_EXTENSION = ".mp4"

    private fun setOnClickListenerListAdapter() {
        remote_list.setOnItemClickListener { parent, view, position, id ->
            if (adapter.folderList[position].isFolder) {
                updateListAdapter(adapter.folderList[position].path)
            } else if (!adapter.folderList[position].isFolder) {
                var file = adapter.folderList[position]
                if (file.name.endsWith(IMAGE_EXTENSION)) {
                    processImage(position)
                } else if (file.name.endsWith(VIDEO_EXTENSION)) {
                    processVideo(file)
                }
            }
        }
    }

    private fun processVideo(file: Folder) {
        val video = getRemoteFile(file, VIDEO_EXTENSION)
        val intent = Intent(Intent.ACTION_VIEW, Uri.fromFile(video))
        intent.setDataAndType(Uri.fromFile(video), "video/mp4")
        startActivity(intent)
    }

    private fun processImage(position: Int) {
        StfalconImageViewer.Builder(this, adapter.folderList.filter { folder -> folder.name.endsWith(IMAGE_EXTENSION) }) { view, image ->
            var image = getRemoteFile(image, IMAGE_EXTENSION)
            Picasso.get().load(image).into(view)
        }.show().setCurrentPosition(position)
    }

    private fun updateListAdapter(path: String) {
        adapter.folderList = getFileList(path)!!.toMutableList()
        adapter.notifyDataSetChanged()
    }

    private fun getFileList(path: String): ArrayList<Folder>? {
        currentPath = path
        currentFolderPathTV.text = currentPath

        return object : AsyncTask<Void, Void, ArrayList<Folder>?>() {
            override fun onPreExecute() {
                super.onPreExecute()
                // progress_overlay.visibility = View.VISIBLE
            }

            override fun doInBackground(vararg params: Void?): ArrayList<Folder>? {
                val folderList = ArrayList<Folder>()
                if (ftpClient.connectionType!!.toLowerCase() == ConnTypes.SFTP) {
                    folderList.addAll(listSftpFiles(path))

                } else if (ftpClient.connectionType!!.toLowerCase() == ConnTypes.FTP) {
                    folderList.addAll(listFtpFile(path))
                }

                return folderList
            }

            override fun onPostExecute(result: ArrayList<Folder>?) {
                super.onPostExecute(result)
                // progress_overlay.visibility = View.INVISIBLE
            }

        }.execute().get()
/*        return object : PadanaAsyncTask(progress_overlay) {
            override fun onPreExecute() {
                super.onPreExecute()
                showProgress()
            }

            override fun doInBackground(vararg voids: Any): Any? {
                val folderList = ArrayList<Folder>()
                if (ftpClient.connectionType!!.toLowerCase() == ConnTypes.SFTP) {
                    folderList.addAll(listSftpFiles(path))

                } else if (ftpClient.connectionType!!.toLowerCase() == ConnTypes.FTP) {
                    folderList.addAll(listFtpFile(path))
                }

                return folderList
            }

            override fun onPostExecute(folderList: Any?) {
                dismissProgress()
            }
        }.execute().get()*/
    }

    private fun listSftpFiles(path: String): ArrayList<Folder> {
        val folderList = ArrayList<Folder>()
        try {
            SFTPUtils.createSFTPConnection(ftpClient)?.let { sftpChan ->
                folderList.addAll(ArrayList(sftpChan.ls(path).filter { file ->
                    !(file as ChannelSftp.LsEntry).filename.startsWith(".") && !file.filename.startsWith("..")
                }.map { file ->
                    val f = file as ChannelSftp.LsEntry
                    Folder(f.filename, file.attrs.isDir, path + "/" + f.filename)
                }))

                sftpChan.disconnect()
            }
        } catch (e: SftpException) {
            e.printStackTrace()
            LogerFileUtils.error(e.message!!)
        }
        return folderList
    }

    private fun listFtpFile(path: String): ArrayList<Folder> {
        val folderList: ArrayList<Folder> = ArrayList()
        val ftp = FTPClient()
        val config = FTPClientConfig()
        ftp.configure(config)
        ftp.connect(ftpClient.server)

        val reply = ftp.replyCode

        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect()
            Snackbar.make(View.inflate(this, R.layout.activity_ftp_explorer, null), getString(R.string.connError), Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.error), null).show()
            return folderList
        }
        ftp.autodetectUTF8 = true
        ftp.controlEncoding = "UTF-8"
        ftp.login(ftpClient.user, ftpClient.password)

        val files = ftp.listFiles(path)

        for (file in files) {
            if (file.name.startsWith(".") || file.name.startsWith("..")) {
                continue
            }
            val folder = Folder(file.name, file.isDirectory, path + "/" + file.name)
            folderList.add(folder)
        }
        ftp.disconnect()
        return folderList
    }

    private fun getRemoteFile(folder: Folder, fileExtension: String): File {
        return object : AsyncTask<Void, Void, File>() {
            override fun doInBackground(vararg params: Void?): File {
                var byteArray: ByteArray?
                var file = File("")
                SFTPUtils.createSFTPConnection(ftpClient)?.let { sftpChan ->
                    var outputFile = File.createTempFile("prefix", fileExtension, applicationContext.cacheDir)
                    byteArray = sftpChan.get(folder.path).readBytes()
                    var fos = FileOutputStream(outputFile)
                    fos.write(byteArray)
                    file = outputFile

                }
                return file
            }
        }.execute().get()
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun onBackPressed() {
        currentPath = currentPath.removeSuffix("/")
        var currentPathArray = currentPath.split("/").toTypedArray()
        currentPathArray = Arrays.copyOf(currentPathArray, currentPathArray.size - 1)
        currentPath = ""
        for (path in currentPathArray) {
            currentPath += "$path/"
        }
        if (currentPath == ftpClient.rootLocation + "/") {
            this.finish()
            return
        }
        updateListAdapter(currentPath)
    }
}
