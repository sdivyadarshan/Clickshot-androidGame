# Clickshot

> **A physics-driven Android recoil shooter built with Kotlin & Jetpack Compose — inspired by classic one-tap recoil arcade games, reimagined and engineered in a deeper, more advanced way.**

---

## Overview & Inspiration

**Clickshot** started as an inspiration to take the addictive simplicity of a classic random one-tap gun-recoil game and **recreate it in a far more advanced, physically authentic, and extensible way**.

Instead of relying on canned animations, fixed spin loops, or hardcoded trajectories, every jump, tumble, bounce, and bank shot in Clickshot emerges live from a **custom 2D rigid-body & projectile physics engine** running at 60 FPS inside a Jetpack Compose `Canvas`. Combined with an expandable weapon arsenal (each with distinct geometry, mass, recoil impulses, and projectile mechanics), multi-stage progression, and Room-backed persistence, Clickshot elevates a casual arcade concept into a responsive physics sandbox.

---

## Core Physics Engine

Clickshot's gameplay is driven by `GameEngine` — a custom continuous-timestep (`dt`-clamped) 2D physics simulation written in pure Kotlin:

### 1. Newtonian Recoil & Mass-Scaled Momentum
- Tapping anywhere in the arena fires from the weapon's true rotated **world-space muzzle offset** (computed dynamically per weapon silhouette).
- An equal-and-opposite **Newtonian recoil impulse** ($\Delta \vec{v} = \vec{J} / m$) propels the gun backward along its firing axis, scaled inversely by the selected weapon's **mass**.
- Light weapons (like the *Viper SMG*) react snappily; heavy weapons (like the *Titan Magnum* or *Arc Railgun*) deliver massive kickback balanced by greater inertia.

### 2. Realistic Lever-Arm Torque, Moment of Inertia & Spin Decay
- Because a firearm's barrel bore axis sits offset above its center of mass, recoil creates a real **lever-arm torque** ($\tau = -d_{\text{lever}} \cdot J$) divided by the weapon's normalized **2D moment of inertia** ($I = m \cdot \frac{w^2 + h^2}{2}$).
- Rather than spinning at an artificial constant speed, rotational velocity is governed by **linear + quadratic aerodynamic damping** ($\omega \leftarrow \omega \cdot [1 - (c_1 + c_2 |\omega|)\Delta t]$). High-speed muzzle flips snap realistically right after firing and smoothly decelerate as angular drag bleeds off excess spin.
- Each shot also triggers a spring-like **slide blowback visual offset** along the receiver.

### 3. Surface Restitution & Contact Friction Torque
- When the gun collides with arena walls, ceiling, floor, or solid shelf platforms, normal velocity reflects via **elastic restitution** while tangential contact friction injects realistic **surface spin torque** — tipping angled weapons naturally when they skid or bounce off barriers.

### 4. Sub-Stepped High-Velocity Ricochet Projectiles
- Projectiles travel at `2,000 – 3,100 px/s` using **4x temporal sub-stepping** per frame to eliminate tunneling through thin platforms or targets.
- Wall and obstacle impacts compute exact **2D normal vector reflection** ($\vec{v}' = (\vec{v} - 2(\vec{v} \cdot \hat{n})\hat{n}) \cdot e$) with customizable bounce limits (`3` to `6` ricochets depending on the weapon), glowing tracer trails, and directional impact sparks.

### 5. Arena-Scaled 2.5x Silhouettes & Shatter Effects
- Weapons and armed bomb targets are rendered at **2.5x close-up scale** inside a high-contrast dark slate chamber with pulsing detonator indicators, muzzle flash particle emitters, and multi-fragment rigid debris explosions upon target elimination.

---

## Weapon Arsenal

Players can switch weapons anytime from the live arsenal bar below the arena:

| Weapon | Mass | Recoil Impulse | Projectile Speed | Max Ricochets | Special Trait |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Pulse** *(Semi-Auto)* | `1.00` | `540` | `2200 px/s` | `4` | Balanced handling & crisp muzzle control |
| **Magnum** *(Heavy)* | `1.45` | `860` | `2750 px/s` | `5` | Heavy frame, ventilated barrel, massive kick |
| **Scatter** *(Shotgun)* | `1.30` | `790` | `2000 px/s` | `3` | Fires a **3-pellet spread burst** for trick bank shots |
| **Viper** *(SMG)* | `0.78` | `440` | `2350 px/s` | `4` | Lightweight chassis with nimble mid-air rotation |
| **Railgun** *(Plasma)* | `1.60` | `920` | `3100 px/s` | `6` | Twin-rail hyper-velocity plasma beam & max bounces |

---

## Level Progression

- **Levels 1 – 5 (Hand-Crafted Distance Scaling):** Progressive range and elevation challenges, starting from forgiving close-quarters alignment in Level 1 to full-chamber cross-arena bank shots in Level 5.
- **Levels 6+ (Procedural Arena Generator):** Deterministic seeded procedural layouts that vary spawn sides, target heights, platform widths, and mid-arena barrier obstacles for endless replayability.

---

## Tech Stack & Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose + Material Design 3 (Edge-to-Edge, `Canvas` 2D rendering, `BoxWithConstraints` responsive arena scaling)
- **Architecture:** MVVM (`GameViewModel` + `StateFlow`) with decoupled pure-Kotlin physics engine (`GameEngine` & `GameEntities`)
- **Local Persistence:** Room Database (`AppDatabase`, `GameRepository`, DAOs & entities for campaign stages, player profile, friends, and match history)
- **Testing:** JUnit + Robolectric (`ExampleRobolectricTest`) & Roborazzi screenshot testing (`GreetingScreenshotTest`)

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge host & top-level screen state wiring
├── game/
│   ├── engine/
│   │   ├── GameEngine.kt            # 2D physics loop, recoil/torque math, ricochet & level setups
│   │   └── GameEntities.kt          # GunType arsenal, GunEntity, TargetEntity, Bullets, Particles
│   └── multiplayer/                 # Multiplayer lobby & match synchronization models
├── ui/
│   ├── screens/
│   │   ├── ArenaGameScreen.kt       # Interactive Canvas chamber, vector weapon renderers & gun bar
│   │   └── ...                      # Campaign, Home, Lobby, Matchmaking, Friends, Profile screens
│   ├── components/                  # Reusable Material 3 UI components
│   ├── theme/                       # ColorScheme, Typography & Shapes
│   └── viewmodel/
│       └── GameViewModel.kt         # 60 FPS coroutine simulation loop & Room state integration
└── data/
    ├── db/                          # Room database & DAOs
    ├── model/                       # Persistence entities
    └── repository/                  # Repository abstraction
```

---

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/<your-username>/clickshot-android.git
   ```
2. Open in **Android Studio** (or build via Gradle CLI):
   ```bash
   gradle assembleDebug
   ```
3. Run unit & physics tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```

---

## How to Play

1. Pick a weapon from the **Arsenal Strip** at the bottom of the screen.
2. **Tap anywhere inside the arena** to fire.
3. Use the **gun's recoil kick and realistic mid-air rotation** to reposition and line up direct hits or **multi-bounce ricochet bank shots** into the armed bomb target.
4. Destroy the target in as few shots as possible to advance to the next stage!
