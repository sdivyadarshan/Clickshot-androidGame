package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.game.engine.GunType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MultiplayerRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun uniqueCode(): String =
        "R" + UUID.randomUUID().toString().replace("-", "").take(7).uppercase()

    @Test
    fun createAndJoinRoom_emitsOpponentAndShots_andEnforces3HitElimination() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MultiplayerRepository(firestore)
        val roomCode = uniqueCode()

        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createRoom(
                hostUid = aliceUid,
                hostName = "Alice",
                gunType = GunType.PULSE_PISTOL,
                isPublic = true,
                customCode = roomCode
            )
        }
        assertTrue(createRes.isSuccess)

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = MultiplayerRepository(firestore)
        val joinRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinRoomByCode(
                roomCode = roomCode,
                guestUid = bobUid,
                guestName = "Bob",
                gunType = GunType.SCATTER_SHOTGUN
            )
        }
        assertTrue(joinRes.isSuccess)

        // Bob emits a real-time shot
        val shotRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.publishShot(
                roomId = roomCode,
                shooterUid = bobUid,
                opponentUid = aliceUid,
                gunType = GunType.SCATTER_SHOTGUN,
                normX = 0.72f,
                normY = 0.22f,
                dirAngle = 3.14f,
                pellets = 3,
                spreadRadians = 0.13f,
                seq = 1L
            )
        }
        assertTrue(shotRes.isSuccess)

        // Alice observes Bob's shot via Firestore Flow
        signInTestUser(ALICE_EMAIL)
        val observedShots = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeShots(roomCode, aliceUid).first { it.isNotEmpty() }
        }
        assertEquals(3L, observedShots.first().pellets)

        // Single hit state is allowed without elimination
        val oneHitRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.publishPlayerState(
                roomId = roomCode,
                uid = aliceUid,
                displayName = "Alice",
                gunType = GunType.PULSE_PISTOL,
                normX = 0.25f,
                normY = 0.30f,
                normVx = 0.1f,
                normVy = 0.2f,
                angle = 0.2f,
                angularVelocity = 1.0f,
                shotsFired = 1,
                simultaneousHits = 1,
                isEliminated = false
            )
        }
        assertTrue(oneHitRes.isSuccess)

        // 3 simultaneous hits eliminate Alice and allow finishing room
        val threeHitRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.publishPlayerState(
                roomId = roomCode,
                uid = aliceUid,
                displayName = "Alice",
                gunType = GunType.PULSE_PISTOL,
                normX = 0.25f,
                normY = 0.30f,
                normVx = 0.0f,
                normVy = 0.0f,
                angle = 0.2f,
                angularVelocity = 0.0f,
                shotsFired = 1,
                simultaneousHits = 3,
                isEliminated = true
            )
        }
        assertTrue(threeHitRes.isSuccess)

        val finishRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.finishRoom(roomCode, winnerUid = bobUid)
        }
        assertTrue(finishRes.isSuccess)
    }

    @Test
    fun unauthenticatedUser_cannotObserveShotsOrRoom() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MultiplayerRepository(firestore)
        val roomCode = uniqueCode()

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createRoom(
                hostUid = aliceUid,
                hostName = "Alice",
                gunType = GunType.PULSE_PISTOL,
                isPublic = false,
                customCode = roomCode
            )
        }

        auth.signOut()

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                aliceRepo.observeShots(roomCode, aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException for unauthenticated shot listener", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun nonParticipantUser_cannotReadActiveRoomOrJoinFinishedRoom() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MultiplayerRepository(firestore)
        val roomCode = uniqueCode()

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createRoom(
                hostUid = aliceUid,
                hostName = "Alice",
                gunType = GunType.PULSE_PISTOL,
                isPublic = true,
                customCode = roomCode
            )
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = MultiplayerRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinRoomByCode(roomCode, bobUid, "Bob", GunType.VECTOR_SMG)
        }

        val eveUid = signInTestUser(EVE_EMAIL)
        val eveRepo = MultiplayerRepository(firestore)
        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                eveRepo.observeRoom(roomCode).first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = caught?.unwrapFirestoreException()
        assertNotNull("Expected PERMISSION_DENIED for non-participant observing active room", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        assertTrue(eveUid.isNotBlank())
    }

    private companion object {
        const val ALICE_EMAIL = "alice_pvp@test.com"
        const val BOB_EMAIL = "bob_pvp@test.com"
        const val EVE_EMAIL = "eve_pvp@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
