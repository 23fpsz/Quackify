package com.quackify.backend.core.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AppPrefEntry::class], version = 1, exportSchema = false)
abstract class AppPrefsDb : RoomDatabase() {
    abstract fun dao(): AppPrefsDao

    companion object {
        @Volatile
        private var instance: AppPrefsDb? = null

        fun get(context: Context): AppPrefsDb {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppPrefsDb::class.java,
                    "app_prefs.db"
                ).allowMainThreadQueries().build().also { instance = it }
            }
        }
    }
}
