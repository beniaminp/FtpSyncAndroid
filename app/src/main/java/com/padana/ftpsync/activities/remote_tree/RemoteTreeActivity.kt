package com.padana.ftpsync.activities.remote_tree

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
import com.padana.ftpsync.activities.remote_tree.holder.TreeNodeHolder
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.folder_list.Folder
import com.padana.ftpsync.services.utils.LogerFileUtils
import com.padana.ftpsync.services.utils.SFTPUtils
import com.padana.ftpsync.utils.ConnTypes
import com.unnamed.b.atv.model.TreeNode
import com.unnamed.b.atv.view.AndroidTreeView
import kotlinx.android.synthetic.main.activity_remote_tree.*
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList


class RemoteTreeActivity : AppCompatActivity() {
    private var currentPath = "/"
    private lateinit var ftpClient: FtpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_tree)

        var bundle = intent.extras
        ftpClient = bundle?.getSerializable("ftpClient") as FtpClient

        var folderList: ArrayList<Folder>? = getFileList(ftpClient.rootLocation + "/" + Build.MODEL)

        val parent = TreeNode(TreeNodeHolder.IconTreeItem(R.mipmap.expand, ftpClient.hostName!!, null)).setViewHolder(TreeNodeHolder(this))

        val root = TreeNode.root()
        parent.addChildren(getChildren(folderList, false))

        root.addChild(parent)

        root.isExpanded = true

        val tView = AndroidTreeView(this, root)
        // tView.setDefaultViewHolder(TreeNodeHolder(this))
        tView.setDefaultNodeClickListener { node, value ->
            (value as TreeNodeHolder.IconTreeItem).folder?.let { folder ->
                if (folder.isFolder && !node.isExpanded && node.children.isEmpty()) {
                    node.addChildren(getChildren(getFileList(folder.path), node.isExpanded))
                }
                if (folder.name.endsWith(".jpg")) {
                    var intent = Intent(Intent.ACTION_VIEW, Uri.parse("file://" + getFileBytes(folder).absolutePath))
                    intent.type = "image/*"
                    startActivity(intent)
                }
            }
        }
        treeLayout.addView(tView.view)
    }

    private fun getChildren(folderList: ArrayList<Folder>?, isExpanded: Boolean): List<TreeNode> {
        return folderList?.let { list ->
            list.map { folder ->
                var icon: Int? = null
                if (folder.isFolder) {
                    if (isExpanded) {
                        icon = R.mipmap.collapse
                    } else {
                        icon = R.mipmap.expand
                    }
                }
                TreeNode(TreeNodeHolder.IconTreeItem(icon, folder.name, folder)).setViewHolder(TreeNodeHolder(this))
            }
        }.orEmpty()
    }

    private fun getFileList(path: String): ArrayList<Folder>? {
        currentPath = path

        return object : AsyncTask<Void, Void, ArrayList<Folder>?>() {
            override fun doInBackground(vararg params: Void?): ArrayList<Folder>? {
                val folderList = ArrayList<Folder>()
                if (ftpClient.connectionType!!.toLowerCase() == ConnTypes.SFTP) {
                    folderList.addAll(listSftpFiles(path))

                } else if (ftpClient.connectionType!!.toLowerCase() == ConnTypes.FTP) {
                    folderList.addAll(listFtpFile(path))
                }

                return folderList
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
                    !(file as ChannelSftp.LsEntry).filename.startsWith(".") && !(file as ChannelSftp.LsEntry).filename.startsWith("..")
                }.map { file ->
                    var f = file as ChannelSftp.LsEntry
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

    private fun getFileBytes(folder: Folder): File {
        return object : AsyncTask<Void, Void, File>() {
            override fun doInBackground(vararg params: Void?): File {
                var byteArray: ByteArray? = null
                var file = File("")
                SFTPUtils.createSFTPConnection(ftpClient)?.let { sftpChan ->
                    byteArray = sftpChan.get(folder.path).readBytes()
                    var outputFile = File.createTempFile("prefix", ".jpg", applicationContext.cacheDir)
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
        if (currentPath == ftpClient.rootLocation + "/" + Build.MODEL + "/") {
            this.finish()
            return
        }
        getFileList(currentPath)
    }
}
