package com.padana.ftpsync.simple.fragments


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.padana.ftpsync.R
import com.padana.ftpsync.entities.FileInfo
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.services.utils.RemoteConnector
import com.padana.ftpsync.simple.fragments.GalleryItemFragment.OnListFragmentInteractionListener
import com.padana.ftpsync.simple.interfaces.RecViewLoadMore
import com.squareup.picasso.Picasso
import com.stfalcon.imageviewer.StfalconImageViewer
import kotlinx.android.synthetic.main.fragment_galleryitem.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class MyGalleryItemRecyclerViewAdapter(
        private val mValues: MutableList<FileInfo>,
        private val mListener: OnListFragmentInteractionListener?,
        private val mLoaderMore: RecViewLoadMore?,
        private val ftpClient: FtpClient)
    : RecyclerView.Adapter<MyGalleryItemRecyclerViewAdapter.ViewHolder>() {
    private lateinit var dialog: AlertDialog
    val remoteConnector = RemoteConnector(ftpClient)

    private val mOnClickListener: View.OnClickListener

    init {
        mOnClickListener = View.OnClickListener { v ->
            val item = v.tag as FileInfo
            var position = 0
            mValues.forEachIndexed { index, fileInfo ->
                if (fileInfo.id == item.id) {
                    position = index
                }
            }
            val salfconBuilder = StfalconImageViewer.Builder(v.context, mValues) { view, image ->
                GlobalScope.launch(Dispatchers.Main) {
                    dialog.show()
                    val image = getRemoteFile(image)
                    dialog.hide()
                    Picasso.get().load(image).into(view)
                }
            }
            salfconBuilder.allowZooming(true)
            val stfalcon: StfalconImageViewer<FileInfo> = salfconBuilder.show(true)
            stfalcon.setCurrentPosition(position)

/*            salfconBuilder.withImageChangeListener { pos ->
                println(position)
                if (pos != position) {
                    stfalcon.setCurrentPosition(pos)
                }
            }*/


            // mListener?.onListFragmentInteraction(item)
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_galleryitem, parent, false)
        val builder = AlertDialog.Builder(parent.context)
        builder.setCancelable(false); // if you want user to wait for some process to finish,
        builder.setView(R.layout.layout_loading_dialog)
        dialog = builder.create()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == mValues.size - 1) {
            GlobalScope.launch(Dispatchers.Main) {
                loadMoreAsync(position)
            }
        }
        val fileInfo = mValues[position]
        GlobalScope.launch(Dispatchers.Main) {
            val image = getRemoteFileThumbanil(fileInfo)
            Picasso.get().load(image).into(holder.mContentView)
            with(holder.mView) {
                tag = fileInfo
                setOnClickListener(mOnClickListener)
            }
        }
    }

    private fun loadMoreAsync(position: Int) {
        mLoaderMore?.onLoadMore(position)
    }

    override fun getItemCount(): Int = mValues.size

    fun addItems(newItems: MutableList<FileInfo>) {
        mValues.addAll(newItems)
        notifyItemInserted(mValues.size - 1)
    }

    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        val mContentView: ImageView = mView.imageView
    }

    private suspend fun getRemoteFileThumbanil(fileInfo: FileInfo): File {
        return withContext(Dispatchers.IO) {
            remoteConnector.getFile(fileInfo.thumbnailLocation!!)
        }
    }

    private suspend fun getRemoteFile(fileInfo: FileInfo): File {
        return withContext(Dispatchers.IO) {
            remoteConnector.getFile(fileInfo.location!!)
        }
    }
}
