package com.quackify.backend.core.store

import androidx.room.Entity

@Entity(tableName = "app_prefs", primaryKeys = ["store", "key"])
data class AppPrefEntry(
    val store: String,
    val key: String,
    val stringValue: String? = null,
    val longValue: Long? = null,
)
