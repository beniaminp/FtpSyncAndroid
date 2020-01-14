package com.padana.ftpsync.dao

import androidx.room.*
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.entities.SyncData

@Dao
interface GenericDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFtpClient(vararg ftpClient: FtpClient)

    @Update
    fun updateFtpClient(vararg ftpClient: FtpClient)

    @Delete
    fun deleteFtpClient(vararg ftpClient: FtpClient)

    @Query("SELECT * FROM ftp_clients")
    fun loadAllFtpClients(): Array<FtpClient>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSyncData(vararg syncData: SyncData)

    @Query("SELECT * FROM sync_data")
    fun loadAllSyncData(): Array<SyncData>

    @Update
    fun updateSyncData(vararg syncData: SyncData)

    @Delete
    fun deleteSyncData(vararg syncData: SyncData)

    @Query("SELECT * FROM sync_data WHERE serverId=:serverId AND localPath=:localPath")
    fun findOneSyncData(serverId: Int, localPath: String): Array<SyncData>

    @Query("DELETE FROM sync_data where serverId=:ftpClientId")
    fun deleteSyncDataByFtpClientId(ftpClientId: Int)

    /* @Query("SELECT * FROM user WHERE age > :minAge")
     fun loadAllUsersOlderThan(minAge: Int): Array<User>*/
}