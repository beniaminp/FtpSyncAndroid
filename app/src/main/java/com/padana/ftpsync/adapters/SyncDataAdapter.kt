package com.padana.ftpsync.adapters

import android.content.Context
import android.os.AsyncTask
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.padana.ftpsync.R
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.SyncData

class SyncDataAdapter(mContext: Context,
                      resource: Int,
                      ftpClientId: Int,
                      syncDataList: MutableList<SyncData>) : ArrayAdapter<SyncData>(mContext, resource, syncDataList) {

    var mContext: Context = mContext
    var syncDataList = syncDataList
    var ftpClientId = ftpClientId

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var listItem: View? = convertView

        if (listItem == null) {
            listItem = LayoutInflater.from(mContext).inflate(
                    R.layout.sync_data_adapter,
                    parent,
                    false
            )
        }
        val syncData: SyncData = syncDataList[position]


        listItem!!.findViewById<TextView>(R.id.tvSyncDataFolder).text = syncData.localPath

        listItem.findViewById<TextView>(R.id.btnSyncDataDelete).setOnClickListener { v ->
            deleteFolderSyncData(syncData.localPath!!)
        }

        return listItem
    }


    override fun getCount(): Int {
        return syncDataList.size;
    }

    private fun deleteFolderSyncData(pathString: String) {
        val thisContext = this
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                var syncData = DatabaseClient(mContext).getAppDatabase().genericDAO.findOneSyncData(ftpClientId!!, pathString)
                if (syncData.isNotEmpty()) {
                    DatabaseClient(mContext).getAppDatabase().genericDAO.deleteSyncData(*syncData)
                }
                return null
            }
        }.execute()
    }
}