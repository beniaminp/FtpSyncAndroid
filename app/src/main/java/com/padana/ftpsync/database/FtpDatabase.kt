package com.padana.ftpsync.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.padana.ftpsync.dao.GenericDAO
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData


@Database(version = 2, entities = [FtpClient::class, SyncData::class])
abstract class FtpDatabase : RoomDatabase() {
    abstract val genericDAO: GenericDAO
}