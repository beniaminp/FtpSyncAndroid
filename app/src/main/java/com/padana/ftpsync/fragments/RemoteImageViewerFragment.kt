package com.padana.ftpsync.fragments

import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.padana.ftpsync.R

class RemoteImageViewerFragment : Fragment() {

    companion object {
        fun newInstance() = RemoteImageViewerFragment()
    }

    private lateinit var viewModel: RemoteImageViewerViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.remote_image_viewer_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(RemoteImageViewerViewModel::class.java)
        // TODO: Use the ViewModel
    }

}
