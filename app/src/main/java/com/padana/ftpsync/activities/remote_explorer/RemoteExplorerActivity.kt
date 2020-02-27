package com.padana.ftpsync.activities.remote_explorer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class RemoteExplorerActivity : AppCompatActivity() {
    private lateinit var currentPath: String
    private lateinit var ftpClient: FtpClient
    private lateinit var adapter: RemoteListAdapter
    private lateinit var dialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_explorer)

        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false); // if you want user to wait for some process to finish,
        builder.setView(R.layout.layout_loading_dialog);
        dialog = builder.create()

        var bundle = intent.extras
        ftpClient = bundle?.getSerializable("ftpClient") as FtpClient
        currentPath = ftpClient.rootLocation + "/" + Build.MODEL
        currentFolderPathTV.text = currentPath

        GlobalScope.launch(Dispatchers.Main) {
            dialog.show()
            val fileList = getFiles(currentPath)!!.toMutableList()
            adapter = RemoteListAdapter(applicationContext, R.layout.remote_list_row, fileList)
            remote_list.adapter = adapter
            dialog.hide()
        }

        setOnClickListenerListAdapter()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.exit -> {
                android.os.Process.killProcess(android.os.Process.myPid())
                true
            }
            R.id.help -> {
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val IMAGE_EXTENSION = ".jpg"
    private val VIDEO_EXTENSION = ".mp4"
    private val TXT_EXTENSION = ".txt"

    private fun setOnClickListenerListAdapter() {
        dialog.show()
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
        dialog.hide()
    }

    private fun processVideo(file: Folder) {
        GlobalScope.launch(Dispatchers.Main) {
            dialog.show()
            val video = getRemoteFile(file, VIDEO_EXTENSION)
            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    val m: Method = StrictMode::class.java.getMethod("disableDeathOnFileUriExposure")
                    m.invoke(null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.fromFile(video))
            intent.setDataAndType(Uri.fromFile(video), "video/mp4")
            dialog.hide()
            startActivity(intent)
        }
    }

    private fun processImage(position: Int) {
        val salfcon = StfalconImageViewer.Builder(this, adapter.folderList.filter { folder -> folder.name.endsWith(IMAGE_EXTENSION) }) { view, image ->
            GlobalScope.launch(Dispatchers.Main) {
                dialog.show()
                val image = getRemoteFile(image, IMAGE_EXTENSION)
                dialog.hide()
                Picasso.get().load(image).into(view)
            }
        }
        salfcon.show().setCurrentPosition(position)

    }

    private fun updateListAdapter(path: String) {
        GlobalScope.launch(Dispatchers.Main) {
            dialog.show()
            adapter.folderList = getFiles(path)!!.toMutableList()
            adapter.notifyDataSetChanged()
            dialog.hide()
        }
    }

    private suspend fun getFiles(path: String): ArrayList<Folder>? {
        currentPath = path
        currentFolderPathTV.text = currentPath
        return withContext(Dispatchers.IO) {
            val folderList = ArrayList<Folder>()
            if (ftpClient.connectionType!!.toLowerCase() == ConnTypes.SFTP) {
                folderList.addAll(listSftpFiles(path))

            } else if (ftpClient.connectionType!!.toLowerCase() == ConnTypes.FTP) {
                folderList.addAll(listFtpFile(path))
            }

            folderList
        }
    }

    private fun listSftpFiles(path: String): ArrayList<Folder> {
        val folderList = ArrayList<Folder>()
        try {
            SFTPUtils.createSFTPConnection(ftpClient)?.let { sftpChan ->
                folderList.addAll(ArrayList(sftpChan.ls(path).filter { file ->
                    !(file as ChannelSftp.LsEntry).filename.startsWith(".") && !file.filename.startsWith("..")
                }.map { file ->
                    val f = file as ChannelSftp.LsEntry
                    val folder = Folder(f.filename, file.attrs.isDir, path + "/" + f.filename)
                    if (!file.attrs.isDir) {
                        folder.size = (f.attrs.size / 1024).toString() + " KB"
                    }
                    val date = Date(f.attrs.mTime * 1000L)
                    val sdf = SimpleDateFormat("dd/mm/yyyy")
                    folder.lastModifiedDate = sdf.format(date)

                    folder
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

    private suspend fun getRemoteFile(folder: Folder, fileExtension: String): File {
        return withContext(Dispatchers.IO) {
            var byteArray: ByteArray?
            var file = File("")
            SFTPUtils.createSFTPConnection(ftpClient)?.let { sftpChan ->
                var outputFile = File.createTempFile("prefix", fileExtension, applicationContext.externalCacheDir)
                byteArray = sftpChan.get(folder.path).readBytes()
                var fos = FileOutputStream(outputFile)
                fos.write(byteArray)
                file = outputFile

            }
            file
        }
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
