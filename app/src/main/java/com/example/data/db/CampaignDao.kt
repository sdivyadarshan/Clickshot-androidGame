package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CampaignStage
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaign_stages ORDER BY levelNumber ASC")
    fun getAllStages(): Flow<List<CampaignStage>>

    @Query("SELECT * FROM campaign_stages WHERE levelNumber = :levelNumber LIMIT 1")
    suspend fun getStage(levelNumber: Int): CampaignStage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStages(stages: List<CampaignStage>)

    @Update
    suspend fun updateStage(stage: CampaignStage)
}
