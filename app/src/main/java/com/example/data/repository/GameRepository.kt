package com.example.data.repository

import com.example.data.db.AppDatabase
import com.example.data.model.CampaignStage
import com.example.data.model.FriendEntity
import com.example.data.model.MatchHistoryEntity
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

class GameRepository(private val database: AppDatabase) {

    val userProfile: Flow<UserProfile?> = database.userDao().getUserProfile()
    val allFriends: Flow<List<FriendEntity>> = database.friendDao().getAllFriends()
    val recentMatches: Flow<List<MatchHistoryEntity>> = database.matchHistoryDao().getRecentMatches()
    val campaignStages: Flow<List<CampaignStage>> = database.campaignDao().getAllStages()

    suspend fun getUserProfileOnce(): UserProfile? = database.userDao().getUserProfileOnce()

    suspend fun saveUserProfile(profile: UserProfile) {
        database.userDao().insertOrUpdate(profile)
    }

    suspend fun updateProfileStats(
        won: Boolean,
        kills: Int,
        ratingDelta: Int,
        coinsEarned: Int,
        xpEarned: Int
    ) {
        val current = getUserProfileOnce() ?: UserProfile()
        val newXp = current.xp + xpEarned
        val newLevel = 1 + (newXp / 500)
        val newRating = (current.rating + ratingDelta).coerceAtLeast(100)
        val updated = current.copy(
            level = newLevel,
            xp = newXp,
            rating = newRating,
            coins = current.coins + coinsEarned,
            totalWins = if (won) current.totalWins + 1 else current.totalWins,
            totalMatches = current.totalMatches + 1
        )
        database.userDao().insertOrUpdate(updated)
    }

    suspend fun addFriend(friend: FriendEntity) {
        database.friendDao().insertFriend(friend)
    }

    suspend fun removeFriend(friendId: String) {
        database.friendDao().deleteFriendById(friendId)
    }

    suspend fun recordMatch(match: MatchHistoryEntity) {
        database.matchHistoryDao().insertMatch(match)
    }

    suspend fun completeCampaignStage(
        stageNumber: Int,
        score: Int,
        stars: Int,
        rewardCoins: Int
    ) {
        val stage = database.campaignDao().getStage(stageNumber)
        if (stage != null) {
            val updatedStage = stage.copy(
                highScore = maxOf(stage.highScore, score),
                stars = maxOf(stage.stars, stars)
            )
            database.campaignDao().updateStage(updatedStage)
        }

        // Unlock next stage
        val nextStageNumber = stageNumber + 1
        val nextStage = database.campaignDao().getStage(nextStageNumber)
        if (nextStage != null && !nextStage.isUnlocked) {
            database.campaignDao().updateStage(nextStage.copy(isUnlocked = true))
        }

        val currentProfile = getUserProfileOnce() ?: UserProfile()
        val newHighest = maxOf(currentProfile.highestCampaignLevel, nextStageNumber)
        database.userDao().insertOrUpdate(
            currentProfile.copy(
                highestCampaignLevel = newHighest,
                coins = currentProfile.coins + rewardCoins,
                xp = currentProfile.xp + 150
            )
        )
    }
}
