package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.game.engine.GunType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@IgnoreExtraProperties
data class RemoteUserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "Pilot",
    val avatarIndex: Long = 0L,
    val onlineWins: Long = 0L,
    val onlineMatches: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "displayName" to displayName,
        "avatarIndex" to avatarIndex,
        "onlineWins" to onlineWins,
        "onlineMatches" to onlineMatches,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class PvpRoom(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val hostUid: String = "",
    val hostName: String = "Host",
    val guestUid: String? = null,
    val guestName: String? = null,
    val status: String = "waiting",
    val isPublic: Boolean = true,
    val winnerUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun opponentUidFor(myUid: String): String? = when (myUid) {
        hostUid -> guestUid
        guestUid -> hostUid
        else -> null
    }

    fun opponentNameFor(myUid: String): String? = when (myUid) {
        hostUid -> guestName
        guestUid -> hostName
        else -> null
    }
}

@IgnoreExtraProperties
data class RemotePlayerState(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "Pilot",
    val gunType: String = GunType.PULSE_PISTOL.name,
    val normX: Double = 0.25,
    val normY: Double = 0.22,
    val normVx: Double = 0.0,
    val normVy: Double = 0.05,
    val angle: Double = 0.0,
    val angularVelocity: Double = 0.0,
    val shotsFired: Long = 0L,
    val simultaneousHits: Long = 0L,
    val isEliminated: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RemoteShotEvent(
    @DocumentId val id: String = "",
    val shooterUid: String = "",
    val participantUids: List<String> = emptyList(),
    val gunType: String = GunType.PULSE_PISTOL.name,
    val normX: Double = 0.25,
    val normY: Double = 0.22,
    val dirAngle: Double = 0.0,
    val pellets: Long = 1L,
    val spreadRadians: Double = 0.0,
    val seq: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { exc -> cont.resumeWithException(exc) }
    }

class MultiplayerRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    suspend fun getOrCreateUserProfile(
        uid: String,
        displayName: String
    ): Result<RemoteUserProfile> {
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().awaitTask()
            if (snap.exists()) {
                val existing = snap.toObject(
                    RemoteUserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) {
                    return Result.success(existing)
                }
            }
            val safeName = displayName.trim().ifBlank { "CyberPilot" }.take(32)
            val profile = RemoteUserProfile(
                id = uid,
                uid = uid,
                displayName = safeName
            )
            docRef.set(profile.toCreateMap()).awaitTask()
            val refreshed = docRef.get().awaitTask()
            val saved = refreshed.toObject(
                RemoteUserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: profile
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordMatchOutcome(uid: String, displayName: String, won: Boolean): Result<Unit> {
        val docRef = db.collection("users").document(uid)
        return try {
            val current = getOrCreateUserProfile(uid, displayName).getOrThrow()
            val updates = mapOf<String, Any>(
                "displayName" to displayName.trim().ifBlank { current.displayName }.take(32),
                "avatarIndex" to current.avatarIndex,
                "onlineWins" to (current.onlineWins + if (won) 1L else 0L),
                "onlineMatches" to (current.onlineMatches + 1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createRoom(
        hostUid: String,
        hostName: String,
        gunType: GunType,
        isPublic: Boolean,
        customCode: String? = null
    ): Result<PvpRoom> {
        val code = (customCode?.trim()?.uppercase()?.replace(Regex("[^A-Z0-9_-]"), "")
            ?.takeIf { it.length in 4..12 }) ?: generateRoomCode()
        val roomRef = db.collection("pvp_rooms").document(code)
        val playerRef = roomRef.collection("players").document(hostUid)
        val safeName = hostName.trim().ifBlank { "HostPilot" }.take(32)

        return try {
            val batch = db.batch()
            val roomData = mapOf<String, Any>(
                "roomCode" to code,
                "hostUid" to hostUid,
                "hostName" to safeName,
                "status" to "waiting",
                "isPublic" to isPublic,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val playerData = mapOf<String, Any>(
                "uid" to hostUid,
                "displayName" to safeName,
                "gunType" to gunType.name,
                "normX" to 0.24,
                "normY" to 0.22,
                "normVx" to 0.0,
                "normVy" to 0.05,
                "angle" to -0.05,
                "angularVelocity" to 0.0,
                "shotsFired" to 0L,
                "simultaneousHits" to 0L,
                "isEliminated" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(roomRef, roomData)
            batch.set(playerRef, playerData)
            batch.commit().awaitTask()

            Result.success(
                PvpRoom(
                    id = code,
                    roomCode = code,
                    hostUid = hostUid,
                    hostName = safeName,
                    status = "waiting",
                    isPublic = isPublic
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, roomRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinRoomByCode(
        roomCode: String,
        guestUid: String,
        guestName: String,
        gunType: GunType
    ): Result<PvpRoom> {
        val code = roomCode.trim().uppercase().replace(Regex("[^A-Z0-9_-]"), "")
        if (code.length !in 4..12) {
            return Result.failure(IllegalArgumentException("Enter a valid 4-12 character room code."))
        }
        val roomRef = db.collection("pvp_rooms").document(code)
        val playerRef = roomRef.collection("players").document(guestUid)
        val safeName = guestName.trim().ifBlank { "GuestPilot" }.take(32)

        return try {
            val snap = roomRef.get().awaitTask()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("Room $code not found."))
            }
            val room = snap.toObject(
                PvpRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Invalid room data."))

            if (room.hostUid == guestUid) {
                return Result.failure(IllegalStateException("You are already hosting room $code."))
            }
            if (room.status != "waiting") {
                return Result.failure(IllegalStateException("Room $code is already ${room.status}."))
            }

            roomRef.update(
                mapOf<String, Any>(
                    "guestUid" to guestUid,
                    "guestName" to safeName,
                    "status" to "playing",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitTask()

            playerRef.set(
                mapOf<String, Any>(
                    "uid" to guestUid,
                    "displayName" to safeName,
                    "gunType" to gunType.name,
                    "normX" to 0.76,
                    "normY" to 0.22,
                    "normVx" to 0.0,
                    "normVy" to 0.05,
                    "angle" to Math.PI,
                    "angularVelocity" to 0.0,
                    "shotsFired" to 0L,
                    "simultaneousHits" to 0L,
                    "isEliminated" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitTask()

            Result.success(
                room.copy(
                    guestUid = guestUid,
                    guestName = safeName,
                    status = "playing"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            Result.failure(e)
        }
    }

    suspend fun findQuickMatchOrCreate(
        uid: String,
        displayName: String,
        gunType: GunType
    ): Result<PvpRoom> {
        val roomsCol = db.collection("pvp_rooms")
        return try {
            val waitingSnap: QuerySnapshot = roomsCol
                .whereEqualTo("status", "waiting")
                .limit(25)
                .get()
                .awaitTask()

            val candidate = waitingSnap.toObjects(
                PvpRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).firstOrNull { it.isPublic && it.hostUid != uid && it.guestUid == null }

            if (candidate != null) {
                val joinResult = joinRoomByCode(candidate.roomCode, uid, displayName, gunType)
                if (joinResult.isSuccess) {
                    return joinResult
                }
            }

            createRoom(
                hostUid = uid,
                hostName = displayName,
                gunType = gunType,
                isPublic = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, roomsCol.path)
            Result.failure(e)
        }
    }

    fun observeRoom(roomId: String): Flow<PvpRoom?> =
        db.collection("pvp_rooms")
            .document(roomId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    PvpRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeOpponentPlayer(roomId: String, opponentUid: String): Flow<RemotePlayerState?> =
        db.collection("pvp_rooms")
            .document(roomId)
            .collection("players")
            .document(opponentUid)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    RemotePlayerState::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    fun observeShots(roomId: String, myUid: String): Flow<List<RemoteShotEvent>> =
        db.collection("pvp_rooms")
            .document(roomId)
            .collection("shots")
            .whereArrayContains("participantUids", myUid)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    RemoteShotEvent::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.seq }
            }

    suspend fun publishPlayerState(
        roomId: String,
        uid: String,
        displayName: String,
        gunType: GunType,
        normX: Float,
        normY: Float,
        normVx: Float,
        normVy: Float,
        angle: Float,
        angularVelocity: Float,
        shotsFired: Int,
        simultaneousHits: Int,
        isEliminated: Boolean
    ): Result<Unit> {
        val docRef = db.collection("pvp_rooms")
            .document(roomId)
            .collection("players")
            .document(uid)
        val safeName = displayName.trim().ifBlank { "Pilot" }.take(32)
        val clampedHits = simultaneousHits.coerceIn(0, 3)
        val finalEliminated = isEliminated && clampedHits >= 3
        val payload = mapOf<String, Any>(
            "displayName" to safeName,
            "gunType" to gunType.name,
            "normX" to normX.toDouble().coerceIn(-0.24, 1.24),
            "normY" to normY.toDouble().coerceIn(-0.24, 1.24),
            "normVx" to normVx.toDouble().coerceIn(-11.5, 11.5),
            "normVy" to normVy.toDouble().coerceIn(-11.5, 11.5),
            "angle" to angle.toDouble().coerceIn(-6.4, 6.4),
            "angularVelocity" to angularVelocity.toDouble().coerceIn(-19.5, 19.5),
            "shotsFired" to shotsFired.toLong().coerceIn(0L, 100000L),
            "simultaneousHits" to clampedHits.toLong(),
            "isEliminated" to finalEliminated,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(payload).awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun publishShot(
        roomId: String,
        shooterUid: String,
        opponentUid: String,
        gunType: GunType,
        normX: Float,
        normY: Float,
        dirAngle: Float,
        pellets: Int,
        spreadRadians: Float,
        seq: Long
    ): Result<Unit> {
        val shotId = "${shooterUid.take(16)}_${seq}_${System.currentTimeMillis()}"
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(64)
        val docRef = db.collection("pvp_rooms")
            .document(roomId)
            .collection("shots")
            .document(shotId)
        val payload = mapOf<String, Any>(
            "shooterUid" to shooterUid,
            "participantUids" to listOf(shooterUid, opponentUid),
            "gunType" to gunType.name,
            "normX" to normX.toDouble().coerceIn(-0.24, 1.24),
            "normY" to normY.toDouble().coerceIn(-0.24, 1.24),
            "dirAngle" to dirAngle.toDouble().coerceIn(-6.4, 6.4),
            "pellets" to pellets.toLong().coerceIn(1L, 5L),
            "spreadRadians" to spreadRadians.toDouble().coerceIn(0.0, 1.0),
            "seq" to seq.coerceIn(1L, 1000000L),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun finishRoom(roomId: String, winnerUid: String): Result<Unit> {
        val roomRef = db.collection("pvp_rooms").document(roomId)
        return try {
            roomRef.update(
                mapOf<String, Any>(
                    "status" to "finished",
                    "winnerUid" to winnerUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            Result.failure(e)
        }
    }

    suspend fun cancelWaitingRoom(roomId: String): Result<Unit> {
        val roomRef = db.collection("pvp_rooms").document(roomId)
        return try {
            roomRef.delete().awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, roomRef.path)
            Result.failure(e)
        }
    }

    companion object {
        fun generateRoomCode(): String {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return (1..6).map { chars.random() }.joinToString("")
        }
    }
}
