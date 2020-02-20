package com.padana.ftpsync.activities.remote_tree.holder


import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.padana.ftpsync.R
import com.padana.ftpsync.folder_list.Folder
import com.unnamed.b.atv.model.TreeNode


class TreeNodeHolder(context: Context?) : TreeNode.BaseNodeViewHolder<TreeNodeHolder.IconTreeItem>(context) {
    var mContext: Context = context!!

    override fun createNodeView(node: TreeNode?, value: IconTreeItem?): View {
        val inflater = LayoutInflater.from(mContext)
        val view: View = inflater.inflate(R.layout.tree_node_holder, null, false)
        val tvValue = view.findViewById<View>(R.id.node_value) as TextView
        tvValue.text = value!!.folderName
        val fileIcon = view.findViewById<View>(R.id.fileIcon) as ImageView
        value.icon?.let { icon ->
            fileIcon.setImageResource(icon)
        }

        return view
    }

    class IconTreeItem(icon: Int?, folderName: String, folder: Folder?) {
        var icon: Int? = icon
        var folderName: String = folderName
        var folder: Folder? = folder
    }
}
