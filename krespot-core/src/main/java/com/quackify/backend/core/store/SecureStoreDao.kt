package com.quackify.backend.core.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SecureStoreDao {
    @Query("SELECT * FROM secure_store")
    fun all(): List<SecureEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entry: SecureEntry)

    @Query("DELETE FROM secure_store WHERE `key` = :key")
    fun remove(key: String)

    @Query("DELETE FROM secure_store")
    fun clear()
}
