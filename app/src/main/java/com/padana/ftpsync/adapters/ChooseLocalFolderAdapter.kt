package com.padana.ftpsync.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import com.padana.ftpsync.R
import com.padana.ftpsync.folder_list.Folder
import com.padana.ftpsync.interfaces.CheckBoxChanged

class ChooseLocalFolderAdapter(var mContext: Context,
                               resource: Int,
                               var folderList: MutableList<Folder>,
                               var onCheckChangedListener: CheckBoxChanged) : ArrayAdapter<Folder>(mContext, resource, folderList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        var listItem: View? = convertView

        if (folderList.size == 0) {
            return listItem!!
        }

        if (listItem == null) {
            listItem = LayoutInflater.from(mContext).inflate(
                    R.layout.adapter_choose_local_folders,
                    parent,
                    false
            )
        }

        val folder: Folder = folderList[position]
        val image = listItem!!.findViewById<ImageView>(R.id.imageView_folder_file)
        if (folder.isFolder) {
            image.setImageResource(R.mipmap.icons8_folder_50)
        } else {
            image.setImageResource(R.mipmap.icons8_file_50)
        }

        val folderName = listItem.findViewById<TextView>(R.id.tv_folder_file_name)
        folderName.text = folder.name

        val itemCheckbox = listItem.findViewById<CheckBox>(R.id.list_view_item_checkbox)

        itemCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            onCheckChangedListener.onCheckBoxChangedState(position, isChecked)
        }
        itemCheckbox.isChecked = folder.isSync

        return listItem
    }

    override fun getCount(): Int {
        return folderList.size
    }
}