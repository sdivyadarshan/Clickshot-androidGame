package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CampaignStage
import com.example.data.model.FriendEntity
import com.example.data.model.MatchHistoryEntity
import com.example.data.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserProfile::class,
        FriendEntity::class,
        MatchHistoryEntity::class,
        CampaignStage::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun friendDao(): FriendDao
    abstract fun matchHistoryDao(): MatchHistoryDao
    abstract fun campaignDao(): CampaignDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cyber_clash_db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getDatabase(context)
                            // Initialize default user
                            val defaultId = "CC-${(1000..9999).random()}"
                            database.userDao().insertOrUpdate(
                                UserProfile(
                                    playerId = defaultId,
                                    displayName = "CyberPilot",
                                    avatarIndex = 0,
                                    level = 1,
                                    xp = 0,
                                    rating = 1200,
                                    coins = 600,
                                    highestCampaignLevel = 1
                                )
                            )
                            // Initialize sample default friends online/offline
                            database.friendDao().insertFriends(
                                listOf(
                                    FriendEntity(
                                        friendId = "CC-4421",
                                        displayName = "HyperNova",
                                        avatarIndex = 1,
                                        rating = 1380,
                                        isOnline = true,
                                        inMatch = false,
                                        statusMessage = "Ready for 1v1 / 2v2!"
                                    ),
                                    FriendEntity(
                                        friendId = "CC-8902",
                                        displayName = "VortexRider",
                                        avatarIndex = 2,
                                        rating = 1520,
                                        isOnline = true,
                                        inMatch = true,
                                        statusMessage = "In Ranked 2v2 match"
                                    ),
                                    FriendEntity(
                                        friendId = "CC-1175",
                                        displayName = "ShadowStriker",
                                        avatarIndex = 3,
                                        rating = 1260,
                                        isOnline = true,
                                        inMatch = false,
                                        statusMessage = "Lobby host: looking for squad"
                                    ),
                                    FriendEntity(
                                        friendId = "CC-6640",
                                        displayName = "PlasmaQueen",
                                        avatarIndex = 4,
                                        rating = 1640,
                                        isOnline = false,
                                        inMatch = false,
                                        statusMessage = "Offline 2h ago"
                                    )
                                )
                            )
                            // Initialize procedural campaign stages (Levels 1 to 20)
                            val modifiers = listOf(
                                "Hyperspeed Velocity" to "Projectiles fly +40% faster and dashing charges quickly.",
                                "Hazard Matrix" to "Roaming plasma sparks periodically sweep the arena grid.",
                                "Titan Overdrive" to "Enemies gain +50% armor shielding and fire triple burst lasers.",
                                "Energy Surge" to "Continuous energy overload! Cooldowns reduced by 50%.",
                                "Vampiric Drain" to "Direct laser impacts heal the attacker for 15 HP.",
                                "EMP Disruptor" to "Arena pulses emit static interference that disables shields.",
                                "Chaos Core" to "Random powerup drops spawn every 8 seconds!"
                            )
                            val stages = (1..20).map { lvl ->
                                val modIndex = (lvl - 1) % modifiers.size
                                val (modName, modDesc) = modifiers[modIndex]
                                val tier = when {
                                    lvl >= 16 -> "CHAOS"
                                    lvl >= 12 -> "NIGHTMARE"
                                    lvl >= 8 -> "HARD"
                                    lvl >= 4 -> "NORMAL"
                                    else -> "EASY"
                                }
                                val bots = when {
                                    lvl >= 15 -> 3
                                    lvl >= 8 -> 2
                                    else -> 1
                                }
                                CampaignStage(
                                    levelNumber = lvl,
                                    title = "Sector $lvl: ${if (lvl % 5 == 0) "Apex Boss" else "Combat Trial"}",
                                    difficultyTier = tier,
                                    modifierName = modName,
                                    modifierDescription = modDesc,
                                    botCount = bots,
                                    targetKills = 2 + (lvl / 3),
                                    isUnlocked = lvl == 1,
                                    stars = 0,
                                    highScore = 0,
                                    rewardCoins = 100 + (lvl * 50)
                                )
                            }
                            database.campaignDao().insertStages(stages)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
