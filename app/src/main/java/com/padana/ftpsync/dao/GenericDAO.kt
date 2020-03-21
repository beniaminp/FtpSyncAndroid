package com.padana.ftpsync.dao

import androidx.room.*
import com.padana.ftpsync.entities.FileInfo
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

    @Query("SELECT * FROM ftp_clients WHERE id=:ftpClientId")
    fun findOneFtpClient(ftpClientId: Int): FtpClient

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

    @Query("SELECT * FROM sync_data WHERE serverId=:serverId")
    fun findSyncDataByFtpClientId(serverId: Int): Array<SyncData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addFileInfo(vararg fileInfo: FileInfo)

    @Update
    fun updateFileInfo(vararg fileInfo: FileInfo)

    @Delete
    fun deleteFileInfo(vararg fileInfo: FileInfo)

    @Query("SELECT * FROM file_info WHERE dateTaken<=:dateTaken")
    fun findAllFileInfoLowerThan(dateTaken: String): Array<FileInfo>

    @Query("SELECT * FROM file_info where serverId=:serverId")
    fun findAllFileInfoByServerId(serverId: Int): Array<FileInfo>

    @Query("SELECT * FROM file_info where serverId=:serverId ORDER BY dateTaken DESC")
    fun findAllFileInfoByServerIdOrderDateDesc(serverId: Int): Array<FileInfo>

    /* @Query("SELECT * FROM user WHERE age > :minAge")
     fun loadAllUsersOlderThan(minAge: Int): Array<User>*/
}