const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const rulesUnitPath = require.resolve("@firebase/rules-unit-testing");
const firebase = require(require.resolve("firebase/compat/app", { paths: [rulesUnitPath] }));
require(require.resolve("firebase/compat/firestore", { paths: [rulesUnitPath] }));

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const EVE_UID = "eve_uid_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTs() {
  return firebase.firestore.FieldValue.serverTimestamp();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or list pvp_rooms or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("pvp_rooms").where("status", "==", "waiting").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("User profile: owner can create/update/read, cross-user blocked, ghost field rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      avatarIndex: 2,
      onlineWins: 0,
      onlineMatches: 0,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Ghost field rejection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      onlineWins: 1,
      isAdmin: true,
      updatedAt: serverTs(),
    })
  );

  // Valid update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      onlineWins: 1,
      onlineMatches: 1,
      updatedAt: serverTs(),
    })
  );
});

test("PvP room lifecycle: host creates in batch with player, guest joins, shots emit, 3-hit elimination finishes & locks room", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  const roomCode = "CK842A";
  const roomRef = aliceDb.collection("pvp_rooms").doc(roomCode);
  const alicePlayerRef = roomRef.collection("players").doc(ALICE_UID);

  // Atomic batch create: room + host player state
  const batch = aliceDb.batch();
  batch.set(roomRef, {
    roomCode,
    hostUid: ALICE_UID,
    hostName: "Alice",
    status: "waiting",
    isPublic: true,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  });
  batch.set(alicePlayerRef, {
    uid: ALICE_UID,
    displayName: "Alice",
    gunType: "PULSE_PISTOL",
    normX: 0.25,
    normY: 0.22,
    normVx: 0.0,
    normVy: 0.05,
    angle: 0.0,
    angularVelocity: 0.0,
    shotsFired: 0,
    simultaneousHits: 0,
    isEliminated: false,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  });
  await assertSucceeds(batch.commit());

  // Quick Match waiting room query alignment
  await assertSucceeds(bobDb.collection("pvp_rooms").where("status", "==", "waiting").get());
  // Unfiltered list fails
  await assertFails(bobDb.collection("pvp_rooms").get());

  // Host cannot spoof someone else or pre-populate winner
  await assertFails(
    eveDb.collection("pvp_rooms").doc("BAD111").set({
      roomCode: "BAD111",
      hostUid: ALICE_UID,
      hostName: "Spoof",
      status: "waiting",
      isPublic: true,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Bob joins waiting room
  await assertSucceeds(
    bobDb.collection("pvp_rooms").doc(roomCode).update({
      guestUid: BOB_UID,
      guestName: "Bob",
      status: "playing",
      updatedAt: serverTs(),
    })
  );

  // Bob creates his player state
  await assertSucceeds(
    bobDb.collection("pvp_rooms").doc(roomCode).collection("players").doc(BOB_UID).set({
      uid: BOB_UID,
      displayName: "Bob",
      gunType: "SCATTER_SHOTGUN",
      normX: 0.75,
      normY: 0.22,
      normVx: 0.0,
      normVy: 0.05,
      angle: 3.14,
      angularVelocity: 0.0,
      shotsFired: 0,
      simultaneousHits: 0,
      isEliminated: false,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Eve (non-participant) cannot read active playing room, player docs, or emit shots
  await assertFails(eveDb.collection("pvp_rooms").doc(roomCode).get());
  await assertFails(eveDb.collection("pvp_rooms").doc(roomCode).collection("players").doc(ALICE_UID).get());
  await assertFails(
    eveDb.collection("pvp_rooms").doc(roomCode).collection("shots").doc("shot_eve").set({
      shooterUid: EVE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      gunType: "PULSE_PISTOL",
      normX: 0.5,
      normY: 0.5,
      dirAngle: 0.0,
      pellets: 1,
      spreadRadians: 0.0,
      seq: 1,
      createdAt: serverTs(),
    })
  );

  // Bob cannot modify Alice's player state
  await assertFails(
    bobDb.collection("pvp_rooms").doc(roomCode).collection("players").doc(ALICE_UID).update({
      simultaneousHits: 3,
      isEliminated: true,
      updatedAt: serverTs(),
    })
  );

  // Alice cannot eliminate herself with < 3 simultaneous hits!
  await assertFails(
    aliceDb.collection("pvp_rooms").doc(roomCode).collection("players").doc(ALICE_UID).update({
      simultaneousHits: 1,
      isEliminated: true,
      updatedAt: serverTs(),
    })
  );

  // Bob emits valid shot
  await assertSucceeds(
    bobDb.collection("pvp_rooms").doc(roomCode).collection("shots").doc("bob_shot_1").set({
      shooterUid: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      gunType: "SCATTER_SHOTGUN",
      normX: 0.72,
      normY: 0.22,
      dirAngle: 3.14,
      pellets: 3,
      spreadRadians: 0.13,
      seq: 1,
      createdAt: serverTs(),
    })
  );

  // Alice observes scoped shots
  await assertSucceeds(
    aliceDb.collection("pvp_rooms").doc(roomCode).collection("shots")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );

  // Shots are immutable
  await assertFails(
    bobDb.collection("pvp_rooms").doc(roomCode).collection("shots").doc("bob_shot_1").update({
      pellets: 5,
    })
  );

  // Alice eliminated after 3 simultaneous hits -> updates player and finishes room
  await assertSucceeds(
    aliceDb.collection("pvp_rooms").doc(roomCode).collection("players").doc(ALICE_UID).update({
      simultaneousHits: 3,
      isEliminated: true,
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("pvp_rooms").doc(roomCode).update({
      status: "finished",
      winnerUid: BOB_UID,
      updatedAt: serverTs(),
    })
  );

  // Terminal state locking: no further updates allowed after finished
  await assertFails(
    bobDb.collection("pvp_rooms").doc(roomCode).update({
      status: "playing",
      winnerUid: ALICE_UID,
      updatedAt: serverTs(),
    })
  );
});
