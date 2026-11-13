package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val friendId: String,
    val displayName: String,
    val avatarIndex: Int = 0,
    val rating: Int = 1200,
    val isOnline: Boolean = true,
    val inMatch: Boolean = false,
    val statusMessage: String = "Ready for battle",
    val addedAt: Long = System.currentTimeMillis()
)
