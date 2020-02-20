package com.padana.ftpsync.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "sync_data")
data class SyncData(
        @PrimaryKey
        var id: Int?,
        var serverId: Int?,
        var localPath: String?,
        var serverPath: String?
) : Serializable