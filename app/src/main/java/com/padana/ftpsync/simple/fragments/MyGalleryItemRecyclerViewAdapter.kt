package com.padana.ftpsync.simple.fragments


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.padana.ftpsync.R
import com.padana.ftpsync.simple.fragments.GalleryItemFragment.OnListFragmentInteractionListener
import com.padana.ftpsync.simple.fragments.dummy.DummyContent.DummyItem
import kotlinx.android.synthetic.main.fragment_galleryitem.view.*


class MyGalleryItemRecyclerViewAdapter(
        private val mValues: MutableList<DummyItem>,
        private val mListener: OnListFragmentInteractionListener?,
        private val mLoaderMore: OnLoadMore?)
    : RecyclerView.Adapter<MyGalleryItemRecyclerViewAdapter.ViewHolder>() {

    private val mOnClickListener: View.OnClickListener

    init {
        mOnClickListener = View.OnClickListener { v ->
            val item = v.tag as DummyItem
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
            mLoaderMore?.onLoadMore(position)
        }
        val item = mValues[position]
        holder.mIdView.text = item.id
        holder.mContentView.text = item.content

        with(holder.mView) {
            tag = item
            setOnClickListener(mOnClickListener)
        }
    }

    override fun getItemCount(): Int = mValues.size

    fun addItems(newItems: MutableList<DummyItem>) {
        mValues.addAll(newItems)
        notifyItemInserted(mValues.size - 1)
    }

    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        val mIdView: TextView = mView.item_number
        val mContentView: TextView = mView.content

        override fun toString(): String {
            return super.toString() + " '" + mContentView.text + "'"
        }
    }

    interface OnLoadMore {
        fun onLoadMore(position: Number)
    }
}
