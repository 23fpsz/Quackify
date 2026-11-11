package com.quackify.backend.core.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SecureEntry::class], version = 1, exportSchema = false)
abstract class SecureStoreDb : RoomDatabase() {
    abstract fun dao(): SecureStoreDao

    companion object {
        @Volatile
        private var instance: SecureStoreDb? = null

        fun get(context: Context): SecureStoreDb {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SecureStoreDb::class.java,
                    "secure_store.db"
                ).allowMainThreadQueries().build().also { instance = it }
            }
        }
    }
}
