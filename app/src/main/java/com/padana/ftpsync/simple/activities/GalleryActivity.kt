package com.padana.ftpsync.simple.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.padana.ftpsync.R
import com.padana.ftpsync.entities.FileInfo
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.simple.fragments.GalleryItemFragment
import com.padana.ftpsync.simple.fragments.dummy.DummyContent

class GalleryActivity : AppCompatActivity(), GalleryItemFragment.OnListFragmentInteractionListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)


    }

    override fun onListFragmentInteraction(item: FileInfo?) {
        TODO("Not yet implemented")
    }

    fun getFtpClient(): FtpClient {
        return intent.extras?.getSerializable("ftpClient") as FtpClient
    }
}
