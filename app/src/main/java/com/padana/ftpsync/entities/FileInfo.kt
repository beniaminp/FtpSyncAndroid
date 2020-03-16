package com.padana.ftpsync.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "file_info")
data class FileInfo(
        @PrimaryKey
        var id: Int?,
        var serverId: Int?,
        var name: String?,
        var dateTaken: String?,
        var thumbnailLocation: String?,
        var location: String?
) : Serializable