package com.padana.ftpsync.simple.fragments


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.padana.ftpsync.MyApp
import com.padana.ftpsync.R
import com.padana.ftpsync.entities.FileInfo
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.services.utils.SFTPUtils
import com.padana.ftpsync.simple.fragments.GalleryItemFragment.OnListFragmentInteractionListener
import com.padana.ftpsync.simple.interfaces.RecViewLoadMore
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_galleryitem.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


class MyGalleryItemRecyclerViewAdapter(
        private val mValues: MutableList<FileInfo>,
        private val mListener: OnListFragmentInteractionListener?,
        private val mLoaderMore: RecViewLoadMore?,
        private val ftpClient: FtpClient)
    : RecyclerView.Adapter<MyGalleryItemRecyclerViewAdapter.ViewHolder>() {

    private val mOnClickListener: View.OnClickListener

    init {
        mOnClickListener = View.OnClickListener { v ->
            val item = v.tag as FileInfo
            // Notify the active callbacks interface (the activity, if the fragment is attached to
            // one) that an item has been selected.
            mListener?.onListFragmentInteraction(item)
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_galleryitem, parent, false)
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
            val image = getRemoteFile(fileInfo)
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

    private suspend fun getRemoteFile(fileInfo: FileInfo): File {
        return withContext(Dispatchers.IO) {
            var byteArray: ByteArray?
            var file = File("")
            SFTPUtils.createSFTPConnection(ftpClient)?.let { sftpChan ->
                var outputFile = File.createTempFile("prefix", ".jpeg", MyApp.getCtx().externalCacheDir)
                byteArray = sftpChan.get(fileInfo.thumbnailLocation).readBytes()
                var fos = FileOutputStream(outputFile)
                fos.write(byteArray)
                file = outputFile

            }
            file
        }
    }
}
