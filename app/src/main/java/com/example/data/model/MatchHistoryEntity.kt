package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "match_history")
data class MatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String, // "SOLO_PVE", "1v1_DUEL", "2v2_TEAM", "CUSTOM_LOBBY"
    val difficultyModifier: String,
    val result: String, // "VICTORY", "DEFEAT", "DRAW"
    val scorePlayer: Int,
    val scoreOpponent: Int,
    val kills: Int = 0,
    val ratingChange: Int = 0,
    val coinsEarned: Int = 0,
    val durationSeconds: Int = 90,
    val timestamp: Long = System.currentTimeMillis()
)
