package com.padana.ftpsync.activities.sync_data_view

import android.os.AsyncTask
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.padana.ftpsync.R
import com.padana.ftpsync.adapters.SyncDataAdapter
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.SyncData
import kotlinx.android.synthetic.main.activity_view_sync_data.*
import kotlinx.android.synthetic.main.content_view_sync_data.*

class ViewSyncDataActivity : AppCompatActivity() {
    var ftpClientId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_sync_data)
        setSupportActionBar(toolbar)

        ftpClientId = intent.getSerializableExtra("ftpClientId") as Int?

        syncDataList.adapter = createSyncDataAdapter(ftpClientId!!, loadSyncDataByFtpClientId())
    }

    private fun createSyncDataAdapter(ftpClientId: Int, syncDataList: Array<SyncData>): SyncDataAdapter {
        return SyncDataAdapter(
                this@ViewSyncDataActivity,
                R.layout.sync_data_adapter,
                ftpClientId,
                syncDataList.toMutableList()
        )
    }

    private fun loadSyncDataByFtpClientId() : Array<SyncData>{
        return object : AsyncTask<Void, Void, Array<SyncData>>() {
            override fun doInBackground(vararg voids: Void): Array<SyncData>? {
                var syncDataList: Array<SyncData> = DatabaseClient(applicationContext).getAppDatabase().genericDAO.findSyncDataByFtpClientId(ftpClientId!!)
                return syncDataList
            }
        }.execute().get()
    }

}
