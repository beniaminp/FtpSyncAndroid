package com.padana.ftpsync.activities.remote_explorer.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.padana.ftpsync.R
import com.padana.ftpsync.folder_list.Folder

class RemoteListAdapter(val mContext: Context,
                        resource: Int,
                        var folderList: MutableList<Folder>) : ArrayAdapter<Folder>(mContext, resource, folderList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        var listItem: View? = convertView

        if (folderList.size == 0) {
            return listItem!!
        }

        if (listItem == null) {
            listItem = LayoutInflater.from(mContext).inflate(
                    R.layout.remote_list_row, null
            )
        }

        val folder: Folder = folderList[position]
        val image = listItem!!.findViewById<ImageView>(R.id.list_image)
        if (folder.isFolder) {
            image.setImageResource(R.mipmap.icons8_folder_50)
        } else {
            if (folder.name.endsWith(".jpg")) {
                image.setImageResource(R.mipmap.image_icon)
            } else if (folder.name.endsWith(".mp4")) {
                image.setImageResource(R.mipmap.video_icon)
            }
        }

        val folderName = listItem.findViewById<TextView>(R.id.item_title)
        folderName.text = folder.name

        val itemDetail = listItem.findViewById<TextView>(R.id.item_details)
        itemDetail.text = folder.size + " \n " + folder.lastModifiedDate

        return listItem
    }

    override fun getCount(): Int {
        return folderList.size
    }
}