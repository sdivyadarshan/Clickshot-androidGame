package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaign_stages")
data class CampaignStage(
    @PrimaryKey val levelNumber: Int,
    val title: String,
    val difficultyTier: String, // "EASY", "NORMAL", "HARD", "EXPERT", "NIGHTMARE", "CHAOS"
    val modifierName: String,
    val modifierDescription: String,
    val botCount: Int = 1,
    val targetKills: Int = 3,
    val isUnlocked: Boolean = false,
    val stars: Int = 0, // 0 to 3
    val highScore: Int = 0,
    val rewardCoins: Int = 150
)
