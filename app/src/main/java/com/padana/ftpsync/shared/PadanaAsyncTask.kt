package com.padana.ftpsync.shared

import android.os.AsyncTask
import android.view.View
import android.widget.FrameLayout

abstract class PadanaAsyncTask(progress_overlay: FrameLayout) : AsyncTask<Any, Any, Any>() {
    var progress_overlay: FrameLayout = progress_overlay

    fun showProgress() {
        progress_overlay.visibility = View.VISIBLE
    }

    fun dismissProgress() {
        progress_overlay.visibility = View.INVISIBLE
    }
}