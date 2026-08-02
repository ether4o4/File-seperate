package com.ether4o4.filevault.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileNodeDao {

    /** Every node, streamed reactively. The tree is assembled in memory. */
    @Query("SELECT * FROM file_nodes ORDER BY isFolder DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FileNode>>

    @Query("SELECT * FROM file_nodes WHERE id = :id")
    suspend fun getById(id: Long): FileNode?

    @Query("SELECT * FROM file_nodes WHERE parentId IS :parentId")
    suspend fun childrenOf(parentId: Long?): List<FileNode>

    @Query("SELECT COUNT(*) FROM file_nodes WHERE parentId IS :parentId AND name = :name AND isFolder = :isFolder")
    suspend fun countNamed(parentId: Long?, name: String, isFolder: Boolean): Int

    @Insert
    suspend fun insert(node: FileNode): Long

    @Update
    suspend fun update(node: FileNode)

    @Delete
    suspend fun delete(node: FileNode)

    @Query("DELETE FROM file_nodes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
