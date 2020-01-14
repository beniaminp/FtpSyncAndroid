package com.padana.ftpsync.activities.ftp_connections

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.padana.ftpsync.R
import com.padana.ftpsync.activities.ChooseLocalFolder
import com.padana.ftpsync.activities.ftp_explorer.FtpExplorerActivity
import com.padana.ftpsync.activities.local_explorer.LocalExplorerActivity
import com.padana.ftpsync.adapters.FtpConnectionsListAdapter
import com.padana.ftpsync.dao.GenericDAO
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.interfaces.BtnClickListener
import com.padana.ftpsync.services.SyncDataService
import com.padana.ftpsync.shared.PadanaAsyncTask
import kotlinx.android.synthetic.main.activity_ftp_connections.*
import kotlinx.android.synthetic.main.content_ftp_connections.*
import kotlinx.android.synthetic.main.progress_dialog.*


class FtpConnectionsActivity : AppCompatActivity() {
    lateinit var genericDAO: GenericDAO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ftp_connections)
        setSupportActionBar(toolbar)

        startService(Intent(this, SyncDataService::class.java))

        genericDAO = DatabaseClient(applicationContext).getAppDatabase().genericDAO

        addFtpConnections.setOnClickListener { view ->
            addFtpConnection()
        }

        populateFtpClientList()
    }

    override fun onResume() {
        super.onResume()
        populateFtpClientList()
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
                }
        )
    }

    private fun editFtpClient(ftpClient: FtpClient) {
        val intent = Intent(this, AddFtpConnectionActivity::class.java)
        intent.putExtra("ftpClient", ftpClient)
        startActivity(intent)
    }

    private fun goToFtpExplorer(ftpClient: FtpClient) {
        val intent = Intent(this, FtpExplorerActivity::class.java)
        intent.putExtra("ftpClient", ftpClient)
        startActivity(intent)
    }

    private fun chooseFolderSync(ftpClient: FtpClient) {
        val intent = Intent(this, ChooseLocalFolder::class.java)
        intent.putExtra("ftpClient", ftpClient)
        startActivity(intent)
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

}
