package com.quackify.backend.core.store

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secure_store")
data class SecureEntry(
    @PrimaryKey val key: String,
    val stringValue: String? = null,
    val longValue: Long? = null,
)
