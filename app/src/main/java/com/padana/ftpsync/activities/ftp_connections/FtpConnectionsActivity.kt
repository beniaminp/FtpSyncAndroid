package com.padana.ftpsync.activities.ftp_connections

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.padana.ftpsync.R
import com.padana.ftpsync.activities.local_explorer.LocalExplorerActivity
import com.padana.ftpsync.activities.remote_explorer.RemoteExplorerActivity
import com.padana.ftpsync.activities.sync_data_view.ViewSyncDataActivity
import com.padana.ftpsync.adapters.FtpConnectionsListAdapter
import com.padana.ftpsync.dao.GenericDAO
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData
import com.padana.ftpsync.interfaces.BtnClickListener
import com.padana.ftpsync.shared.PadanaAsyncTask
import com.padana.ftpsync.simple.activities.GalleryActivity
import com.padana.ftpsync.simple.services.utils.MediaUtils
import com.padana.ftpsync.utils.FileHelpers
import kotlinx.android.synthetic.main.activity_ftp_connections.*
import kotlinx.android.synthetic.main.content_ftp_connections.*
import kotlinx.android.synthetic.main.progress_dialog.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class FtpConnectionsActivity : AppCompatActivity() {
    lateinit var genericDAO: GenericDAO
    lateinit var syncDataServiceIntent: Intent
    var selectedFtpClient: FtpClient? = null
    private val RECORD_REQUEST_CODE = 101
    private val TAG = "PermissionDemo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ftp_connections)
        setupPermissions()

        genericDAO = DatabaseClient(applicationContext).getAppDatabase().genericDAO

        addFtpConnections.setOnClickListener { view ->
            addFtpConnection()
        }

        populateFtpClientList()
        setSupportActionBar(toolbar)

        /*syncDataServiceIntent = Intent(this, SyncDataService::class.java)
        Thread(Runnable {
            run {
                startService(syncDataServiceIntent)
            }
        }).start()*/
    }

    override fun onResume() {
        super.onResume()
        populateFtpClientList()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9999) {
            if (data != null && data.data != null) {
                val uri: Uri = data.data!!
                val docUri: Uri = DocumentsContract.buildDocumentUriUsingTree(uri,
                        DocumentsContract.getTreeDocumentId(uri))
                val localPath: String? = FileHelpers.getLocalPath(this, docUri)
                val path: String? = FileHelpers.getServerPath(this, docUri)

                insertFolderSyncData(path!!, selectedFtpClient!!, localPath!!)
                selectedFtpClient = null
            }
        }
    }

    private fun addFtpConnection() {
        val intent = Intent(this, AddFtpConnectionActivity::class.java)
        startActivity(intent)
    }

    private fun populateFtpClientList() {
        object : PadanaAsyncTask(progress_overlay) {
            override fun onPreExecute() {
                super.onPreExecute()
                showProgress()
            }

            override fun doInBackground(vararg params: Any?): Any? {
                return genericDAO.loadAllFtpClients().toList()
            }

            override fun onPostExecute(ftpClientListRes: Any) {
                val ftpClientList = ftpClientListRes as List<FtpClient>
                val ftpListAdapter = createFtpConnectionsListAdapter(ftpClientList)
                fptConnectionsList.adapter = ftpListAdapter
                dismissProgress()
            }

        }.execute()
    }

    private fun createFtpConnectionsListAdapter(ftpClientList: List<FtpClient>): FtpConnectionsListAdapter {
        return FtpConnectionsListAdapter(
                this@FtpConnectionsActivity,
                R.layout.adapter_ftp_client,
                ftpClientList.toMutableList(),
                object : BtnClickListener {
                    override fun onBtnClick(position: Int) {
                        deleteFtpClient(ftpClientList[position])
                    }
                },
                object : BtnClickListener {
                    override fun onBtnClick(position: Int) {
                        editFtpClient(ftpClientList[position])
                    }
                },
                object : BtnClickListener {
                    override fun onBtnClick(position: Int) {
                        goToFtpExplorer(ftpClientList[position])
                    }
                },
                object : BtnClickListener {
                    override fun onBtnClick(position: Int) {
                        editSyncClient(ftpClientList[position])
                    }
                },
                object : BtnClickListener {
                    override fun onBtnClick(position: Int) {
                        startSyncData(ftpClientList[position])
                    }
                },
                object : BtnClickListener {
                    override fun onBtnClick(position: Int) {
                        chooseFolderSync(ftpClientList[position])
                    }
                },
                object : BtnClickListener {
                    override fun onBtnClick(position: Int) {
                        goToSyncDataView(ftpClientList[position])
                    }
                },
                object : BtnClickListener {
                    override fun onBtnClick(position: Int) {
                        btnSimpleView(ftpClientList[position])
                    }
                }
        )
    }

    private fun editFtpClient(ftpClient: FtpClient) {
        val intent = Intent(this, AddFtpConnectionActivity::class.java)
        intent.putExtra("ftpClient", ftpClient)
        startActivity(intent)
    }

    private fun goToFtpExplorer(ftpClient: FtpClient) {
        // val intent = Intent(this, FtpExplorerActivity::class.java)
        val intent = Intent(this, RemoteExplorerActivity::class.java)
        intent.putExtra("ftpClient", ftpClient)
        startActivity(intent)
    }

    private fun btnSimpleView(ftpClient: FtpClient) {
        // val intent = Intent(this, FtpExplorerActivity::class.java)
        val intent = Intent(this, GalleryActivity::class.java)
        intent.putExtra("ftpClient", ftpClient)
        startActivity(intent)
    }

    private fun goToSyncDataView(ftpClient: FtpClient) {
        val intent = Intent(this, ViewSyncDataActivity::class.java)
        intent.putExtra("ftpClientId", ftpClient.id!!)
        startActivity(intent)
    }

    private fun chooseFolderSync(ftpClient: FtpClient) {
        /* val intent = Intent(this, ChooseLocalFolder::class.java)
         intent.putExtra("ftpClient", ftpClient)
         startActivity(intent)*/
        selectedFtpClient = ftpClient
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        i.addCategory(Intent.CATEGORY_DEFAULT)
        startActivityForResult(Intent.createChooser(i, "Choose directory"), 9999)
    }

    private fun deleteFtpClient(ftpClient: FtpClient) {
        object : PadanaAsyncTask(progress_overlay) {
            override fun onPreExecute() {
                super.onPreExecute()
                showProgress()
            }

            override fun doInBackground(vararg params: Any?): Any? {
                genericDAO.deleteSyncDataByFtpClientId(ftpClient.id!!)
                genericDAO.deleteFtpClient(ftpClient)
                return null
            }

            override fun onPostExecute(result: Any?) {
                populateFtpClientList()
                dismissProgress()
            }
        }.execute()
    }

    private fun editSyncClient(ftpClient: FtpClient) {
        val intent = Intent(this, LocalExplorerActivity::class.java)
        intent.putExtra("ftpClient", ftpClient)
        startActivity(intent)
    }

    private fun startSyncData(ftpClient: FtpClient) {

    }

    private fun insertFolderSyncData(folderPath: String, ftpClient: FtpClient, localPath: String) {
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                var syncData = SyncData(null, ftpClient!!.id, localPath, ftpClient.rootLocation + "/" + Build.MODEL + "/" + folderPath)
                DatabaseClient(applicationContext).getAppDatabase().genericDAO.insertSyncData(syncData)
                return null
            }
        }.execute()
    }

    private fun setupPermissions() {
        val permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        makeRequest()
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission to record denied")
            makeRequest()
        }
    }

    private fun makeRequest() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                RECORD_REQUEST_CODE)
    }


    override fun onBackPressed() {
        moveTaskToBack(true)
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
                stopService(syncDataServiceIntent)
                android.os.Process.killProcess(android.os.Process.myPid())
                true
            }
            R.id.help -> {
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            RECORD_REQUEST_CODE -> {

                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {

                    Log.i(TAG, "Permission has been denied by user")
                } else {
                    Log.i(TAG, "Permission has been granted by user")
                }
            }
        }
    }

}
