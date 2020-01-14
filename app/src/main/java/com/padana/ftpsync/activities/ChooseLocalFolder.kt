package com.padana.ftpsync.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.padana.ftpsync.R
import com.padana.ftpsync.activities.local_explorer.LocalExplorerActivity
import com.padana.ftpsync.adapters.ChooseLocalFolderAdapter
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import com.padana.ftpsync.folder_list.Folder
import com.padana.ftpsync.interfaces.CheckBoxChanged
import kotlinx.android.synthetic.main.activity_choose_local_folder.*
import java.io.File


class ChooseLocalFolder : AppCompatActivity() {
    private var currentPath = ROOT_STORAGE
    private var folderList: List<Folder> = ArrayList()
    private var ftpClient: FtpClient? = null
    private var listView: ListView? = null
    private var mAdapter: ChooseLocalFolderAdapter? = null
    private val pathTextViews: MutableList<TextView> = ArrayList()

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_local_folder)
        setSupportActionBar(toolbar)

        askForReadPermission()

        ftpClient = (intent.getSerializableExtra("ftpClient") as FtpClient)

        listView = findViewById(R.id.choose_local_folder_list)

        mAdapter = createListAdapter()
        listView!!.adapter = mAdapter

        loadAllSyncData()

        listView!!.setOnItemClickListener { parent, view, position, id ->
            val folder: Folder = (listView!!.adapter as ChooseLocalFolderAdapter).folderList[position]

            if (!folder.isFolder) {
                Snackbar.make(listView!!, "Selected file is not a folder!", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()
                return@setOnItemClickListener
            }
            currentPath = folder.path
            updateListViewFromPath(currentPath)
        }

    }

    private fun askForReadPermission() {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) { //ask for permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), LocalExplorerActivity.READ_STORAGE_PERMISSION_REQUEST_CODE)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBackPressed() {
        val currentPathSplitted = currentPath.split("/").dropLast(1)
        if (currentPathSplitted.size == 1) {
            this.finish()
        }
        currentPath = currentPathSplitted.joinToString("/")
        updateListViewFromPath(currentPath)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun createListAdapter(): ChooseLocalFolderAdapter {
        currentPath = LocalExplorerActivity.ROOT_STORAGE
        changePathView("/sdcard")
        return ChooseLocalFolderAdapter(this,
                R.layout.adapter_choose_local_folders,
                getFoldersForPath(currentPath).toMutableList(),
                doOnCheckChangedListener)
    }

    private val doOnCheckChangedListener: CheckBoxChanged
        get() {
            return object : CheckBoxChanged {
                override fun onCheckBoxChangedState(position: Int, isChecked: Boolean) {
                    val folder: Folder = (listView!!.adapter as ChooseLocalFolderAdapter).folderList[position]
                    folder.isSync = isChecked
                    if (isChecked) {
                        insertFolderSyncData(folder)
                    } else {
                        deleteFolderSyncData(folder)
                    }
                }
            }
        }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun getFoldersForPath(path: String?): List<Folder> {
        val files = File(path).listFiles()
        var folderList: MutableList<Folder> = ArrayList()
        if (files != null) {
            for (file in files) {
                if (file.name.startsWith(".") || file.name.startsWith("..")) {
                    continue
                }
                val folder = Folder(file.name, file.isDirectory, file.path)
                folderList.add(folder)
            }
        } else {
            Snackbar.make(listView!!, "Folder is empty!", Snackbar.LENGTH_LONG)
                    .setAction("Error", null).show()
        }
        folderList = folderList.sortedWith(compareBy({ it.name })).toMutableList()
        return folderList
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun updateListViewFromPath(currentPath: String) {
        folderList = getFoldersForPath(currentPath)
        changePathView(currentPath)
        mAdapter!!.folderList = folderList.toMutableList()
        mAdapter!!.notifyDataSetInvalidated()
        loadAllSyncData()
    }

    private fun loadAllSyncData() {
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                var syncDataList: Array<SyncData> = DatabaseClient(applicationContext).getAppDatabase().genericDAO.loadAllSyncData()
                syncDataList.forEach { syncData ->
                    if (ftpClient!!.id == syncData.serverId) {
                        (listView!!.adapter as ChooseLocalFolderAdapter).folderList.forEach { folder ->
                            if (folder.path == syncData.localPath) {
                                folder.isSync = true
                            }
                        }
                    }
                }
                return null
            }

            override fun onPostExecute(result: Void?) {
                mAdapter!!.notifyDataSetChanged()
            }
        }.execute()
    }

    private fun insertFolderSyncData(folder: Folder) {
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                var syncData = SyncData(null, ftpClient!!.id, folder.path, "/" + Build.MODEL + folder.path)
                DatabaseClient(applicationContext).getAppDatabase().genericDAO.insertSyncData(syncData)
                return null
            }
        }.execute()
    }

    private fun deleteFolderSyncData(folder: Folder) {
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                var syncData = DatabaseClient(applicationContext).getAppDatabase().genericDAO.findOneSyncData(ftpClient!!.id!!, folder.path)
                if (syncData.size > 0) {
                    DatabaseClient(applicationContext).getAppDatabase().genericDAO.deleteSyncData(*syncData)
                }
                return null
            }
        }.execute()
    }

    private fun changePathView(currentPath: String) {
        val pathContainerLayout = findViewById<View>(R.id.pathContainer) as LinearLayout
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        var textView = TextView(this)

        textView.text = currentPath.split("/")[currentPath.split("/").size - 1] + " > "
        textView.gravity = Gravity.LEFT
        textView.tag = currentPath.split("/")[currentPath.split("/").size - 1]

        if (!checkAndRemove(textView)) {
            pathTextViews.add(textView)
            pathContainerLayout.addView(textView, lp)
        }

    }

    private fun checkAndRemove(textView: TextView): Boolean {
        pathTextViews.forEachIndexed { index, tv ->
            if (tv.tag == textView.tag) {
                val pathContainerLayout = findViewById<View>(R.id.pathContainer) as LinearLayout
                try {
                    pathContainerLayout.removeViewAt(index + 1)
                    pathTextViews.removeAt(index)
                } catch (e: Exception) {
                    e.printStackTrace()
                    this.finish()
                }
                return true
            }
        }
        return false
    }

    companion object {
        const val ROOT_STORAGE = "/sdcard/"
        const val READ_STORAGE_PERMISSION_REQUEST_CODE = 0x3
    }
}
