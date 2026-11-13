# Clickshot

> **A physics-driven Android recoil shooter & worldwide 1v1 PvP arena game built with Kotlin, Jetpack Compose, and Firebase — inspired by classic one-tap recoil arcade games, reimagined and engineered in a deeper, more advanced way.**

---

## Overview & Inspiration

**Clickshot** began as an inspiration to take the addictive simplicity of a classic random one-tap gun-recoil game and **recreate it in a far more advanced, physically authentic, and extensible way**.

Instead of canned animations, fixed spin loops, or scripted trajectories, every kickback, mid-air flip, wall bounce, and multi-bank ricochet in Clickshot emerges live from a **custom 2D rigid-body & projectile physics engine** running at **60 FPS** inside a Jetpack Compose `Canvas`.

What started as a solo physics challenge now includes:
- **5 distinct firearms** with real mass, lever-arm torque, and aerodynamic spin decay
- **2.5x scaled close-up arena silhouettes** with high-contrast recoil blowback and particle bursts
- **Procedural & progressive solo stages** with an in-game **Levels Menu** and **Pause Mode weapon switching**
- **Low-latency `SoundPool` audio** featuring unique per-gun firing effects and armor/ricochet acoustics
- **Worldwide Internet 1v1 Multiplayer** powered by **Firebase Authentication (Google Sign-In)** and **Cloud Firestore** — playable across any two devices on any network, governed by a **3-Simultaneous-Hit Elimination Rule**

---

## Core Physics Engine

Clickshot's simulation is powered by `GameEngine` — a continuous-timestep (`dt`-clamped) 2D rigid-body and ballistics engine written in pure Kotlin:

### 1. Newtonian Recoil & Mass-Scaled Momentum
- Tapping anywhere in the arena fires from the active weapon's rotated **world-space muzzle offset**, calculated dynamically per firearm geometry.
- An equal-and-opposite **Newtonian recoil impulse** ($\Delta \vec{v} = \vec{J} / m$) drives the gun backward along its barrel axis, scaled inversely by weapon **mass**.
- Light chassis weapons (*Viper*) launch effortlessly across the chamber, while heavy frames (*Magnum*, *Railgun*) deliver thunderous recoil damped by higher inertia.

### 2. Realistic Lever-Arm Torque, Moment of Inertia & Spin Decay
- Because the barrel bore sits above the center of mass, every shot injects a real **lever-arm torque** divided by the weapon's normalized **2D moment of inertia** ($I = m \cdot \frac{w^2 + h^2}{2}$).
- Rotational velocity decays organically via **linear + quadratic aerodynamic damping** ($\omega \leftarrow \omega \cdot [1 - (c_1 + c_2 |\omega|)\Delta t]$), creating snappy muzzle flips right after firing that settle smoothly mid-air.
- Every shot triggers a spring-like **slide blowback kick** along the weapon receiver.

### 3. Surface Restitution & Contact Friction Torque
- Collisions with arena boundaries and solid steel platforms apply **elastic surface restitution** alongside tangential contact friction torque, causing angled firearms to tip and tumble naturally upon landing or skidding.
- In Solo mode, guns spawn high in the top chamber with a gentle downward drop (`vy = 55–65 px/s`) so players always have time to aim and react.

### 4. Sub-Stepped High-Velocity Ricochet Projectiles
- Projectiles travel at **`2,000 – 3,100 px/s`** using **4x temporal sub-stepping** per frame to prevent tunneling through thin barriers, targets, or enemy guns.
- Wall, obstacle, and central bunker impacts calculate exact **2D normal vector reflection** ($\vec{v}' = (\vec{v} - 2(\vec{v} \cdot \hat{n})\hat{n}) \cdot e$) with weapon-specific bounce limits (`3` to `6` ricochets), glowing plasma trails, and impact sparks.

### 5. Rotated OBB Bullet Collision (PvP)
- In multiplayer duels, incoming projectiles are tested against each gun's **rotated Oriented Bounding Box (OBB)** rather than an approximate circle, matching the 2.5x weapon silhouette accurately at any spin angle.

---

## Weapon Arsenal

Switch weapons live from the **Arsenal Strip** below the arena or inside the **Pause Menu**:

| Weapon | Mass | Recoil Impulse | Projectile Speed | Max Ricochets | Special Trait |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Pulse** *(Semi-Auto)* | `1.00` | `540` | `2,200 px/s` | `4` | Balanced handling & crisp muzzle control |
| **Magnum** *(Heavy)* | `1.45` | `860` | `2,750 px/s` | `5` | Heavy ventilated barrel & massive recoil kick |
| **Scatter** *(Shotgun)* | `1.30` | `790` | `2,000 px/s` | `3` | Fires a **3-pellet spread burst** — capable of instant 3-hit kills |
| **Viper** *(SMG)* | `0.78` | `440` | `2,350 px/s` | `4` | Lightweight frame with rapid mid-air spin agility |
| **Railgun** *(Plasma)* | `1.60` | `920` | `3,100 px/s` | `6` | Twin-rail hyper-velocity beam & maximum bank-shot bounces |

---

## Worldwide Online Multiplayer & 3-Simultaneous-Hit Rule

Clickshot supports **real-time internet multiplayer** between any two devices anywhere in the world — no local Wi-Fi or localhost required.

### Matchmaking Modes
- **Quick Match Worldwide:** Queries Firebase Cloud Firestore for an open waiting duel and pairs two players automatically, or opens a fresh public room if none are waiting.
- **Private Room Codes:** Host a 6-character alphanumeric room code (tap-to-copy) and share it with a friend anywhere on any network to join the exact same combat chamber.

### The 3-Simultaneous-Hit Elimination Rule
Unlike one-tap insta-kill modes, **a single bullet cannot kill a rival gun**:
1. **1 or 2 Hits = Armor Damage Only:** Striking an opponent once or twice applies ballistic knockback, hit-flash feedback, sparks, and `ARMOR_HIT` metallic audio — but the gun stays alive (`1/3` or `2/3`).
2. **1.8-Second Simultaneous Burst Window:** Each hit starts/refreshes a **`1.8s` burst timer** displayed as a shrinking progress bar and floating `0/3` armor pips above both guns. If the attacker fails to land the 3rd hit before the timer runs out, armor recovers back to **`0/3`**.
3. **3 Simultaneous / Burst Hits = Armor Break:** Landing **3 hits simultaneously** (for example, a point-blank 3-pellet **Scatter** burst or stacked ricochet trajectories) or **3 rapid hits within the 1.8s window** triggers `ARMOR_BREAK`, shatters the enemy weapon into physics debris, and awards the duel victory.
4. **Database-Enforced Integrity:** `firestore.rules` enforces `(!data.isEliminated || data.simultaneousHits >= 3)` at the backend security layer so no client can forge a 1-hit elimination.

### Zero-Trust Security & Authentication
- **Navigation-Level Auth Gate:** Online PvP requires **Google Sign-In** via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) before creating any Firestore listeners or `MultiplayerViewModel` instances. Solo mode remains completely playable offline.
- **Scoped Access:** Only the two room participants (`hostUid`, `guestUid`) can read/write active duel telemetry (`players/{uid}` and `shots`), with strict schema, type, bounds, and state-transition checks in `firestore.rules`.

---

## Solo Progression & Controls

- **Levels 1 – 5 (Distance-Scaled Progression):** Progressive spacing from forgiving close-quarters calibration in Level 1 up to full-chamber cross-arena bank shots in Level 5.
- **Levels 6+ (Procedural Arena Generator):** Deterministic seeded layouts varying spawn sides, bomb elevations, platform widths, and mid-arena ricochet barriers for endless replayability.
- **Levels Menu:** Jump directly to any unlocked stage or replay earlier levels from the interactive stage selector.
- **Pause Mode:** Freeze the physics simulation mid-flight to swap firearms, mute/unmute audio, restart, or switch levels.

---

## Audio Engine

`SoundEffectsManager` powers low-latency game audio via Android **`SoundPool`** (`maxStreams = 8`):
- **Distinct Gun Audio:** Every weapon in the 5-gun arsenal plays a unique firing signature (`SHOT_PULSE`, `SHOT_MAGNUM`, `SHOT_SCATTER`, `SHOT_SMG`, `SHOT_RAILGUN`).
- **Tactile Arena Feedback:** Dedicated sounds for bullet ricochets, wall bounces, non-lethal armor hits (`ARMOR_HIT`), 3-hit shatter (`ARMOR_BREAK`), bomb explosions, and stage win/loss cues.
- **Custom Drop-In Overrides:** Automatically loads optional audio files placed in `app/src/main/res/raw/` (e.g., Pixabay firearm SFX named `gun_pulse`, `gun_magnum`, `gun_scatter`, `gun_smg`, `gun_railgun`) and falls back seamlessly to built-in 44.1 kHz synthesized multi-layered WAV soundscapes.

---

## Tech Stack & Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose + Material Design 3 (Edge-to-Edge, `Canvas` 2D rendering, `BoxWithConstraints` adaptive arena scaling)
- **Architecture:** MVVM (`GameViewModel`, `MultiplayerViewModel` + `StateFlow` / `collectAsStateWithLifecycle`) with a decoupled pure-Kotlin physics engine (`GameEngine` & `GameEntities`)
- **Cloud Backend & Auth:** Firebase Authentication (Google Sign-In via Jetpack `CredentialManager`) + Cloud Firestore (named database, real-time snapshot listeners, security rules)
- **Local Persistence:** Room Database (`AppDatabase`, `GameRepository`) for solo campaign progress & profile data
- **Audio:** Android `SoundPool` with procedural 44.1 kHz PCM/WAV synthesis & `res/raw/` loader
- **Branding:** Custom adaptive launcher icon (`ic_launcher_clickshot`)
- **Testing:**
  - `ExampleRobolectricTest`: Solo physics, 2.5x dimensions, weapon switching, audio events, and 3-simultaneous-hit PvP elimination tests
  - `MultiplayerRepositoryRuleTest`: Kotlin Robolectric tests running against local Firebase Auth & Firestore emulators
  - `firestore.test.js`: Node.js `@firebase/rules-unit-testing` security rules verification

---

## Project Structure

```text
├── firestore.rules                            # Zero-trust Firestore security rules (incl. 3-hit invariant)
├── firestore.test.js                          # JS Emulator rules test suite
├── firebase-blueprint.json                    # Firestore schema blueprint
├── firebase.json                              # Local Firebase Auth & Firestore emulator config
└── app/src/
    ├── main/java/com/example/
    │   ├── MainActivity.kt                    # Edge-to-edge host & Solo / Online PvP screen routing
    │   ├── game/
    │   │   ├── audio/
    │   │   │   └── SoundEffectsManager.kt     # SoundPool engine + 44.1 kHz SFX synthesizer
    │   │   ├── engine/
    │   │   │   ├── GameEngine.kt              # 2D physics loop, recoil/torque, ricochet & 3-hit PvP rules
    │   │   │   └── GameEntities.kt            # 5-gun arsenal, GunEntity, Bullets, Bomb Target, Particles
    │   │   └── multiplayer/                   # Local lobby & match models
    │   ├── data/
    │   │   ├── db/                            # Room database & DAOs (solo persistence)
    │   │   ├── model/                         # Local persistence entities
    │   │   ├── remote/
    │   │   │   ├── GoogleAuthManager.kt       # Credential Manager Google Sign-In + Firebase Auth
    │   │   │   ├── MultiplayerRepository.kt   # Firestore room/player/shot sync & matchmaking
    │   │   │   └── FirestoreErrorHandler.kt   # Structured error logging & exception unwrapping
    │   │   └── repository/                    # Solo repository abstraction
    │   └── ui/
    │       ├── screens/
    │       │   ├── ArenaGameScreen.kt         # 60 FPS Canvas arena, 2.5x guns, PvP 0/3 HUD, Pause & Levels UI
    │       │   ├── OnlineMultiplayerScreen.kt # Auth gate, Quick Match, Room Code lobby & PvP arena host
    │       │   └── ...                        # Campaign, Home, Lobby, Matchmaking, Friends, Profile screens
    │       ├── components/                  # Reusable Material 3 UI components
    │       ├── theme/                       # ColorScheme, Typography & Shapes
    │       └── viewmodel/
    │           ├── GameViewModel.kt           # Solo loop, Pause/Levels state & Room integration
    │           └── MultiplayerViewModel.kt    # Online PvP match lifecycle, pose/shot sync & 3-hit updates
    └── test/java/com/example/
        ├── ExampleRobolectricTest.kt          # Physics, audio & 3-simultaneous-hit PvP unit tests
        ├── base/FirestoreEmulatorTestBase.kt  # Robolectric Firebase emulator test harness
        └── data/remote/
            └── MultiplayerRepositoryRuleTest.kt # Kotlin Firestore rules & matchmaking tests
```

---

## Getting Started & Running Tests

### 1. Build the Android App
```bash
gradle assembleDebug
```

### 2. Run Physics & Solo/PvP Unit Tests
```bash
gradle :app:testDebugUnitTest --tests 'com.example.ExampleRobolectricTest'
```

### 3. Run Firebase Security Rules Tests (Emulators)
Start the local Auth & Firestore emulators:
```bash
firebase emulators:start --only firestore,auth --project demo-no-project
```
Run the JS rules tests:
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```
Run the Kotlin Robolectric rule tests:
```bash
gradle :app:testDebugUnitTest --tests '*Rule*'
```

---

## How to Play

### Solo Campaign
1. Choose a firearm from the **Arsenal Strip** (or inside the **Pause Menu**).
2. **Tap anywhere in the chamber** to fire — use recoil momentum and realistic muzzle spin to line up direct hits or multi-bounce **ricochet bank shots** into the armed yellow bomb.
3. Open the **Levels Menu** anytime to jump between stages.

### Online PvP (Worldwide 1v1)
1. Tap **Online PvP** from the top bar and sign in with your **Google account**.
2. Choose **Quick Match Worldwide** to pair with any online challenger, or **Create / Join a Room Code** to battle a friend anywhere.
3. Outmaneuver your opponent inside the shared chamber and **land 3 hits simultaneously or within the 1.8s burst window** (`3/3`) to shatter their weapon and claim victory!
