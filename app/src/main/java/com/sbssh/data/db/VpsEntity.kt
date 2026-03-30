package com.sbssh.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vps")
data class VpsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alias: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: String, // "PASSWORD" or "KEY"
    val password: String? = null,
    val keyContent: String? = null,
    val keyPassphrase: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
