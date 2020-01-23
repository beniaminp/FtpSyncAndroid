package com.padana.ftpsync.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "ftp_clients")
data class FtpClient(
        @PrimaryKey
        var id: Int?,
        var server: String?,
        var user: String?,
        var password: String?,
        var rootLocation: String?,
        var connectionType: String?,
        var hostName: String?
) : Serializable