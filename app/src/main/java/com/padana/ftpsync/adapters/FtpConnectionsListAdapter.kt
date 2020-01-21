package com.padana.ftpsync.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.padana.ftpsync.R
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.interfaces.BtnClickListener

class FtpConnectionsListAdapter(mContext: Context,
                                resource: Int,
                                ftpClientsList: MutableList<FtpClient>,
                                editClickListener: BtnClickListener,
                                deleteClickListener: BtnClickListener,
                                itemClickListener: BtnClickListener,
                                syncClickListener: BtnClickListener,
                                startSyncClickListener: BtnClickListener,
                                chooseFolderSync: BtnClickListener,
                                viewSyncData: BtnClickListener) : ArrayAdapter<FtpClient>(mContext, resource, ftpClientsList) {

    var mContext: Context = mContext
    var ftpClientsList = ftpClientsList
    val editClickListener = editClickListener
    val deleteClickListener = deleteClickListener
    val itemClickListener = itemClickListener
    val syncClickListener = syncClickListener
    val startSyncClickListener = startSyncClickListener
    val chooseFolderSync = chooseFolderSync
    val viewSyncData = viewSyncData

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        var listItem: View? = convertView

        if (ftpClientsList.size == 0) {
            return listItem!!
        }
        if (listItem == null) {
            listItem = LayoutInflater.from(mContext).inflate(
                    R.layout.adapter_ftp_client,
                    parent,
                    false
            )
        }
        val ftpClient: FtpClient = ftpClientsList[position]
        listItem!!.findViewById<TextView>(R.id.tvFtpClientName).text = ftpClient.server

        listItem.findViewById<TextView>(R.id.tvFtpClientName).setOnClickListener { itemClickListener.onBtnClick(position) }
        listItem.findViewById<TextView>(R.id.btnFtpClientDelete).setOnClickListener { editClickListener.onBtnClick(position) }
        listItem.findViewById<TextView>(R.id.btnFtpClientEdit).setOnClickListener { deleteClickListener.onBtnClick(position) }
        listItem.findViewById<TextView>(R.id.btnFtpClientSync).setOnClickListener { syncClickListener.onBtnClick(position) }
        listItem.findViewById<TextView>(R.id.btnStartSync).setOnClickListener { startSyncClickListener.onBtnClick(position) }
        listItem.findViewById<TextView>(R.id.btnFtpClientChooseFolders).setOnClickListener { chooseFolderSync.onBtnClick(position) }
        listItem.findViewById<TextView>(R.id.btnViewSyncData).setOnClickListener { viewSyncData.onBtnClick(position) }


        return listItem
    }

    override fun getCount(): Int {
        return ftpClientsList.size;
    }
}