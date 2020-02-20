package com.padana.ftpsync.database

import android.content.Context
import androidx.room.Room

class DatabaseClient(mCtx: Context) {
    private val mCtx = mCtx
    private val appDatabase: FtpDatabase? = Room.databaseBuilder(mCtx, FtpDatabase::class.java, "ftp-client")
            .fallbackToDestructiveMigration()
            .build()
    private var mInstance: DatabaseClient? = null

    @Synchronized
    private fun getInstance(): DatabaseClient {
        if (mInstance == null) {
            mInstance = DatabaseClient(mCtx)
        }
        return mInstance!!
    }

    fun getAppDatabase(): FtpDatabase {
        return appDatabase!!
    }
}