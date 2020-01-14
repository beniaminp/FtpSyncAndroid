package com.padana.ftpsync.activities.local_explorer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ListView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.padana.ftpsync.R
import com.padana.ftpsync.activities.ftp_explorer.FtpExplorerActivity
import com.padana.ftpsync.adapters.FolderListAdapter
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.folder_list.Folder
import com.padana.ftpsync.interfaces.BtnClickListener
import java.io.File
import java.util.*


class LocalExplorerActivity : AppCompatActivity() {
    private var listView: ListView? = null
    private var mAdapter: FolderListAdapter? = null
    private var currentPath = ROOT_STORAGE
    private var folderList: List<Folder> = ArrayList()
    private var ftpClient: FtpClient? = null
    // private var linkedFolders: Array<SyncData>? = null


    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        ftpClient = (intent.getSerializableExtra("ftpClient") as FtpClient)

        listView = findViewById(R.id.folder_list)
        askForReadPermission()

        // linkedFolders = DatabaseClient(this).getAppDatabase().genericDAO.loadAllSyncData()

        mAdapter = createListAdapter()
        listView!!.adapter = mAdapter

    }

    private fun listBtnClickListener(): BtnClickListener {
        return object : BtnClickListener {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun onBtnClick(position: Int) {
                navigateToFolder(position)
            }
        }
    }

    private fun linkBtnClickListener(): BtnClickListener {
        return object : BtnClickListener {
            @RequiresApi(Build.VERSION_CODES.N)
            override fun onBtnClick(position: Int) {
                goToFtpExplorer(position)
            }
        }
    }

    private fun goToFtpExplorer(position: Int) {
        val intent = Intent(this, FtpExplorerActivity::class.java)
        var extras = Bundle()
        extras.putSerializable("ftpClient", ftpClient)
        extras.putSerializable("localFolder", mAdapter!!.folderList[position])
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun createListAdapter(): FolderListAdapter {
        return FolderListAdapter(this,
                R.layout.adapter_choose_local_folders,
                getFoldersForPath(ROOT_STORAGE).toMutableList(),
                listBtnClickListener(),
                true,
                linkBtnClickListener())
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun navigateToFolder(position: Int) {
        val folder = mAdapter!!.folderList[position]
        if (!folder.isFolder) {
            Snackbar.make(listView!!, "Selected file is not a folder!", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            return
        }
        currentPath = folder.path
        updateListViewFromPath(currentPath)
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
        if (currentPath.equals("/")) {
            this.finish()
            return
        }
        updateListViewFromPath(currentPath)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean { // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { // Handle action bar item clicks here. The action bar will
// automatically handle clicks on the Home/Up button, so long
// as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        return if (id == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun updateListViewFromPath(currentPath: String) {
        folderList = getFoldersForPath(currentPath)
        mAdapter!!.folderList = folderList.toMutableList()
        mAdapter!!.notifyDataSetInvalidated()
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

    private fun askForReadPermission() {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) { //ask for permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_STORAGE_PERMISSION_REQUEST_CODE)
            }
        }
    }

    companion object {
        const val ROOT_STORAGE = "/sdcard/"
        const val READ_STORAGE_PERMISSION_REQUEST_CODE = 0x3
    }
}