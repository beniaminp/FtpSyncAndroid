package com.padana.ftpsync.services.utils

import android.os.AsyncTask
import java.net.InetAddress
import java.net.UnknownHostException

object InternetUtils {
    fun isInternetAvailable(): Boolean {
        return object : AsyncTask<Void, Void, Boolean>() {
            override fun doInBackground(vararg voids: Void): Boolean {
                try {
                    val address: InetAddress = InetAddress.getByName("www.google.com")
                    return !address.equals("")
                } catch (e: UnknownHostException) {
                    LogerFileUtils.error(e.message!!)// Log error
                }
                return false
            }
        }.execute().get()

    }
}