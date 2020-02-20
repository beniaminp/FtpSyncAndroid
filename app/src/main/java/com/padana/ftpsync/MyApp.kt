package com.padana.ftpsync

import android.app.Application
import android.content.Context

class MyApp : Application() {
    companion object {
        lateinit var instance: MyApp
            private set

        fun getCtx(): Context {
            return instance.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        MyApp.instance = this
    }
}