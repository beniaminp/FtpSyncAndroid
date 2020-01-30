package com.padana.ftpsync.adapters

import android.content.Context
import android.graphics.Color
import android.os.AsyncTask
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.padana.ftpsync.R
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.SyncData
import com.padana.ftpsync.folder_list.Folder
import com.padana.ftpsync.interfaces.BtnClickListener


class FolderListAdapter(mContext: Context,
                        resource: Int,
                        folderList: MutableList<Folder>,
                        clickListener: BtnClickListener? = null,
                        showChooseFolder: Boolean = false,
                        linkClickListener: BtnClickListener? = null,
                        selectFolderClickListener: BtnClickListener? = null) : ArrayAdapter<Folder>(mContext, resource, folderList) {

    var mContext: Context = mContext
    var folderList = folderList
    var showChooseFolder = showChooseFolder
    val clickListener = clickListener
    val linkClickListener = linkClickListener
    val selectFolderClickListener = selectFolderClickListener
    var linkedFolders: Array<SyncData>? = loadAllSyncData()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        var listItem: View? = convertView

        if (folderList.size == 0) {
            return listItem!!
        }

        if (listItem == null) {
            listItem = LayoutInflater.from(mContext).inflate(
                    R.layout.adapter_file_list,
                    parent,
                    false
            )
        }

        listItem!!.findViewById<TextView>(R.id.btnChooseRemoteFolder).visibility = View.INVISIBLE
        listItem.findViewById<TextView>(R.id.btnSelectFolder).visibility = View.INVISIBLE

        if (showChooseFolder) {
            listItem.findViewById<TextView>(R.id.btnChooseRemoteFolder).visibility = View.VISIBLE
        }

        var folder: Folder = folderList[position]
        var image = listItem!!.findViewById<ImageView>(R.id.imageView_folder_file)
        if (folder.isFolder) {
            image.setImageResource(R.mipmap.icons8_folder_50)
        } else {
            image.setImageResource(R.mipmap.icons8_file_50)
        }

        var folderName = listItem.findViewById<TextView>(R.id.tv_folder_file_name)
        folderName.text = folder.name

        if (clickListener != null) {
            listItem.findViewById<TextView>(R.id.tv_folder_file_name).setOnClickListener { clickListener.onBtnClick(position) }
        }

        if (linkClickListener != null) {
            if (linkedFolders!!.filter { syncData -> syncData.localPath == folder.path }.isNotEmpty()) {
                listItem.findViewById<TextView>(R.id.btnChooseRemoteFolder).setBackgroundColor(Color.RED)
            }
            listItem.findViewById<TextView>(R.id.btnChooseRemoteFolder).visibility = View.VISIBLE
            listItem.findViewById<TextView>(R.id.btnChooseRemoteFolder).setOnClickListener { linkClickListener.onBtnClick(position) }
        }

        if (selectFolderClickListener != null) {
            listItem.findViewById<TextView>(R.id.btnSelectFolder).visibility = View.VISIBLE
            listItem.findViewById<TextView>(R.id.btnSelectFolder).setOnClickListener { selectFolderClickListener.onBtnClick(position) }
        }

        val params = listItem.layoutParams
        params.height = 40
        listItem.layoutParams = params

        return listItem
    }

    override fun getCount(): Int {
        return folderList.size
    }

    private fun loadAllSyncData(): Array<SyncData>? {
        return object : AsyncTask<Void, Void, Array<SyncData>?>() {
            override fun doInBackground(vararg voids: Void): Array<SyncData>? {
                return DatabaseClient(mContext).getAppDatabase().genericDAO.loadAllSyncData()
            }
        }.execute().get()
    }
}
