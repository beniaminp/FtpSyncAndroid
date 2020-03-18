package com.padana.ftpsync.simple.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.padana.ftpsync.R
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FileInfo
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.simple.activities.GalleryActivity
import com.padana.ftpsync.simple.fragments.dummy.DummyContent
import com.padana.ftpsync.simple.fragments.dummy.DummyContent.DummyItem
import com.padana.ftpsync.simple.interfaces.RecViewLoadMore
import com.padana.ftpsync.utils.Partition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryItemFragment : Fragment() {

    // TODO: Customize parameters
    private var columnCount = 3

    private var listener: OnListFragmentInteractionListener? = null
    private var recAdapter: MyGalleryItemRecyclerViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_galleryitem_list, container, false)
        val galleryActivity: GalleryActivity = activity as GalleryActivity

        val ftpClient = galleryActivity.getFtpClient()
        GlobalScope.launch {
            val fileInfos = getAllFileInfos(ftpClient)
            val partitions = Partition.ofSize(fileInfos.toMutableList(), 25)
            var currentPartition = 0

            val onLoadMore = object : RecViewLoadMore {
                override fun onLoadMore(position: Number) {
                    recAdapter?.addItems(partitions.get(currentPartition))
                }
            }

            // Set the adapter
            if (view is RecyclerView) {
                with(view) {
                    layoutManager = when {
                        columnCount <= 1 -> LinearLayoutManager(context)
                        else -> GridLayoutManager(context, columnCount)
                    }
                    recAdapter = MyGalleryItemRecyclerViewAdapter(partitions.get(currentPartition), listener, onLoadMore, ftpClient)
                    currentPartition += 1
                    adapter = recAdapter
                }
            }
        }
        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnListFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnListFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     */
    interface OnListFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onListFragmentInteraction(item: FileInfo?)
    }

    companion object {

        // TODO: Customize parameter argument names
        const val ARG_COLUMN_COUNT = "column-count"

        // TODO: Customize parameter initialization
        @JvmStatic
        fun newInstance(columnCount: Int) =
                GalleryItemFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_COLUMN_COUNT, columnCount)
                    }
                }
    }

    private suspend fun getAllFileInfos(ftpClient: FtpClient) =
            withContext(Dispatchers.Default) {
                DatabaseClient(context!!).getAppDatabase().genericDAO.findAllFileInfoByServerIdOrderDateDesc(ftpClient.id!!)
            }
}
