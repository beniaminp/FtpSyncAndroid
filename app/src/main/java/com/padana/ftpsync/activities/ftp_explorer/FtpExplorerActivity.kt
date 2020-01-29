package com.padana.ftpsync.activities.ftp_explorer

import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ListView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.padana.ftpsync.R
import com.padana.ftpsync.adapters.FolderListAdapter
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import com.padana.ftpsync.folder_list.Folder
import com.padana.ftpsync.interfaces.BtnClickListener
import com.padana.ftpsync.shared.PadanaAsyncTask
import kotlinx.android.synthetic.main.activity_ftp_explorer.*
import kotlinx.android.synthetic.main.progress_dialog.*
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPReply
import java.util.*


class FtpExplorerActivity : AppCompatActivity() {
    private var currentPath = "/"
    private lateinit var ftpClient: FtpClient
    private lateinit var localFolder: Folder
    private var mAdapter: FolderListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ftp_explorer)
        setSupportActionBar(toolbar)

        btnCreateFolder.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        var bundle = intent.extras
        ftpClient = bundle?.getSerializable("ftpClient") as FtpClient
        localFolder = bundle.getSerializable("localFolder") as Folder

        getFtpFileList("/")
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
        if (currentPath == "/") {
            this.finish()
            return
        }
        getFtpFileList(currentPath)
    }

    private fun getFtpFileList(path: String) {
        currentPath = path
        var thisContext = this

        object : PadanaAsyncTask(progress_overlay) {
            override fun onPreExecute() {
                super.onPreExecute()
                showProgress()
            }

            override fun doInBackground(vararg voids: Any): Any? {
                var ftp = FTPClient()
                var config = FTPClientConfig()
                ftp.configure(config)
                ftp.connect(ftpClient!!.server)

                var reply = ftp.replyCode

                if (!FTPReply.isPositiveCompletion(reply)) {
                    ftp.disconnect()
                    Snackbar.make(View.inflate(thisContext, R.layout.activity_ftp_explorer, null), getString(R.string.connError), Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.error), null).show()
                    return null
                }
                ftp.autodetectUTF8 = true
                ftp.controlEncoding = "UTF-8"
                ftp.login(ftpClient.user, ftpClient.password)

                val files = ftp.listFiles(path)
                val folderList = ArrayList<Folder>()

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

            override fun onPostExecute(folderList: Any?) {
                val folderList = folderList as ArrayList<Folder>
                var listView = findViewById<ListView>(R.id.ftp_folder_list)
                mAdapter = createListAdapter(thisContext, folderList)
                listView.adapter = mAdapter
                dismissProgress()
            }
        }.execute()
    }

    private fun createListAdapter(thisContext: FtpExplorerActivity, folderList: ArrayList<Folder>) =
            FolderListAdapter(
                    thisContext,
                    R.layout.adapter_file_list,
                    folderList.toMutableList(),
                    listBtnClickListener(),
                    false,
                    null,
                    selectClickListener())

    private fun listBtnClickListener(): BtnClickListener {
        return object : BtnClickListener {
            override fun onBtnClick(position: Int) {
                var folder: Folder = mAdapter!!.folderList[position]
                if (folder.isFolder) {
                    getFtpFileList(folder.path)
                }
            }
        }
    }

    private fun selectClickListener(): BtnClickListener {
        return object : BtnClickListener {
            override fun onBtnClick(position: Int) {
                var folder: Folder = mAdapter!!.folderList[position]
                if (folder.isFolder) {
                    saveSyncData(folder)
                }
            }
        }
    }

    private fun saveSyncData(remoteFolder: Folder) {
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                var syncData = SyncData(null, ftpClient.id, localFolder.path, remoteFolder.path)
                DatabaseClient(applicationContext).getAppDatabase().genericDAO.insertSyncData(syncData)
                this@FtpExplorerActivity.finish()
                return null
            }
        }.execute()
    }
}
