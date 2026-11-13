package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val playerId: String = "CC-7842",
    val displayName: String = "CyberPilot",
    val avatarIndex: Int = 0,
    val level: Int = 1,
    val xp: Int = 0,
    val rating: Int = 1200,
    val coins: Int = 500,
    val highestCampaignLevel: Int = 1,
    val totalWins: Int = 0,
    val totalMatches: Int = 0,
    val preferredShip: String = "Vortex-1"
) {
    val winRate: Float
        get() = if (totalMatches > 0) (totalWins.toFloat() / totalMatches.toFloat()) * 100f else 0f

    val rankTier: String
        get() = when {
            rating >= 2400 -> "Cyber Legend"
            rating >= 2000 -> "Diamond Vanguard"
            rating >= 1600 -> "Platinum Ace"
            rating >= 1300 -> "Gold Striker"
            rating >= 1000 -> "Silver Duelist"
            else -> "Bronze Rookie"
        }
}
