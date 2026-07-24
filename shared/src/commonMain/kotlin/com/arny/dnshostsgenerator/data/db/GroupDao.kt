package com.arny.dnshostsgenerator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroup(group: GroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDomains(domains: List<DomainEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDomain(domain: DomainEntity): Long

    @Transaction
    @Query("SELECT * FROM groups ORDER BY id")
    fun getAllGroupsWithDomains(): Flow<List<GroupWithDomains>>

    @Transaction
    @Query("SELECT * FROM groups WHERE isEnabled = 1 ORDER BY id")
    fun getEnabledGroupsWithDomains(): Flow<List<GroupWithDomains>>

    @Query("UPDATE groups SET isEnabled = :enabled WHERE id = :groupId")
    suspend fun setGroupEnabled(groupId: Long, enabled: Boolean)

    @Query("UPDATE groups SET isEnabled = :enabled")
    suspend fun setAllGroupsEnabled(enabled: Boolean)

    @Query("UPDATE groups SET name = :name WHERE id = :groupId")
    suspend fun updateGroupName(groupId: Long, name: String)

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("UPDATE domains SET domain = :domain WHERE id = :domainId")
    suspend fun updateDomain(domainId: Long, domain: String)

    @Query("DELETE FROM domains WHERE id = :domainId")
    suspend fun deleteDomain(domainId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM domains WHERE domain = :domain)")
    suspend fun domainExists(domain: String): Boolean

    @Query("SELECT COUNT(*) FROM domains WHERE groupId NOT IN (SELECT id FROM groups)")
    suspend fun countOrphanDomains(): Int
}
