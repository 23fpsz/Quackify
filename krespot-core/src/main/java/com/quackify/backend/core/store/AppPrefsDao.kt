package com.quackify.backend.core.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppPrefsDao {
    @Query("SELECT * FROM app_prefs WHERE store = :store")
    fun all(store: String): List<AppPrefEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entry: AppPrefEntry)

    @Query("DELETE FROM app_prefs WHERE store = :store AND `key` = :key")
    fun remove(store: String, key: String)

    @Query("DELETE FROM app_prefs WHERE store = :store")
    fun clear(store: String)
}
