package com.example.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.game.engine.GunType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

enum class GameSoundEvent(val rawResName: String) {
    SHOT_PULSE("gun_pulse"),
    SHOT_MAGNUM("gun_magnum"),
    SHOT_SCATTER("gun_scatter"),
    SHOT_SMG("gun_smg"),
    SHOT_RAILGUN("gun_railgun"),
    BULLET_RICOCHET("sfx_ricochet"),
    GUN_BOUNCE("sfx_bounce"),
    TARGET_EXPLOSION("sfx_explosion"),
    LEVEL_WON("sfx_win"),
    LEVEL_LOST("sfx_lose"),
    UI_CLICK("sfx_click");

    companion object {
        fun forGun(gunType: GunType): GameSoundEvent = when (gunType) {
            GunType.PULSE_PISTOL -> SHOT_PULSE
            GunType.HEAVY_DEAGLE -> SHOT_MAGNUM
            GunType.SCATTER_SHOTGUN -> SHOT_SCATTER
            GunType.VECTOR_SMG -> SHOT_SMG
            GunType.RAIL_CANNON -> SHOT_RAILGUN
        }
    }
}

/**
 * Low-latency SoundPool game SFX engine.
 * Loads optional custom audio files from res/raw/ (e.g., Pixabay gun SFX) if present,
 * and generates realistic multi-layered 44.1 kHz WAV firearm & arena sound effects as built-in defaults.
 */
class SoundEffectsManager(private val context: Context? = null) {

    private val sampleRate = 44100
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var soundPool: SoundPool? = null
    private val soundIds = ConcurrentHashMap<GameSoundEvent, Int>()
    private val loadedIds = ConcurrentHashMap<Int, Boolean>()

    @Volatile
    var isMuted: Boolean = false

    init {
        if (context != null) {
            initSoundPool(context)
        }
    }

    private fun initSoundPool(ctx: Context) {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attrs)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    loadedIds[sampleId] = true
                }
            }
            soundPool = pool

            scope.launch {
                val sfxDir = File(ctx.cacheDir, "clickshot_sfx_v2").apply { mkdirs() }
                GameSoundEvent.entries.forEach { event ->
                    try {
                        val rawId = ctx.resources.getIdentifier(event.rawResName, "raw", ctx.packageName)
                        val loadedSoundId = if (rawId != 0) {
                            pool.load(ctx, rawId, 1)
                        } else {
                            val wavFile = File(sfxDir, "${event.rawResName}.wav")
                            if (!wavFile.exists() || wavFile.length() < 100L) {
                                val pcm = synthesizeEvent(event)
                                writeWavFile(wavFile, pcm)
                            }
                            pool.load(wavFile.absolutePath, 1)
                        }
                        if (loadedSoundId > 0) {
                            soundIds[event] = loadedSoundId
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (_: Throwable) {
            // Safe fallback on headless/JVM test environments
        }
    }

    fun play(event: GameSoundEvent) {
        if (isMuted) return
        val pool = soundPool ?: return
        val id = soundIds[event] ?: return
        if (loadedIds[id] != true) return

        try {
            val rate = when (event) {
                GameSoundEvent.BULLET_RICOCHET -> 0.94f + Random.nextFloat() * 0.14f
                GameSoundEvent.GUN_BOUNCE -> 0.92f + Random.nextFloat() * 0.16f
                else -> 1.0f
            }
            pool.play(id, 1.0f, 1.0f, 1, 0, rate)
        } catch (_: Throwable) {
        }
    }

    private fun synthesizeEvent(event: GameSoundEvent): ShortArray = when (event) {
        GameSoundEvent.SHOT_PULSE -> synthGunshot(
            durationMs = 210,
            punchStartHz = 240f,
            punchEndHz = 62f,
            punchDecay = 32f,
            crackLevel = 0.85f,
            tailDecay = 19f,
            bodyGain = 0.88f,
            echoDelayMs = 38,
            echoLevel = 0.24f,
            mechClickAtMs = 90,
            seed = 101
        )
        GameSoundEvent.SHOT_MAGNUM -> synthGunshot(
            durationMs = 360,
            punchStartHz = 165f,
            punchEndHz = 36f,
            punchDecay = 18f,
            crackLevel = 1.0f,
            tailDecay = 11f,
            bodyGain = 1.0f,
            echoDelayMs = 52,
            echoLevel = 0.34f,
            mechClickAtMs = 135,
            seed = 202
        )
        GameSoundEvent.SHOT_SCATTER -> synthShotgunBlast(durationMs = 380)
        GameSoundEvent.SHOT_SMG -> synthGunshot(
            durationMs = 150,
            punchStartHz = 310f,
            punchEndHz = 92f,
            punchDecay = 42f,
            crackLevel = 0.72f,
            tailDecay = 27f,
            bodyGain = 0.78f,
            echoDelayMs = 28,
            echoLevel = 0.18f,
            mechClickAtMs = 55,
            seed = 404
        )
        GameSoundEvent.SHOT_RAILGUN -> synthRailgun(durationMs = 320)
        GameSoundEvent.BULLET_RICOCHET -> synthRicochet(durationMs = 150)
        GameSoundEvent.GUN_BOUNCE -> synthGunBounce(durationMs = 110)
        GameSoundEvent.TARGET_EXPLOSION -> synthExplosion(durationMs = 520)
        GameSoundEvent.LEVEL_WON -> synthArpeggio(
            notesHz = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f),
            noteMs = 90,
            gain = 0.62f
        )
        GameSoundEvent.LEVEL_LOST -> synthArpeggio(
            notesHz = floatArrayOf(349.23f, 293.66f, 220.00f),
            noteMs = 125,
            gain = 0.55f
        )
        GameSoundEvent.UI_CLICK -> synthUiClick(durationMs = 45)
    }

    private fun synthGunshot(
        durationMs: Int,
        punchStartHz: Float,
        punchEndHz: Float,
        punchDecay: Float,
        crackLevel: Float,
        tailDecay: Float,
        bodyGain: Float,
        echoDelayMs: Int,
        echoLevel: Float,
        mechClickAtMs: Int,
        seed: Int
    ): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val dry = FloatArray(count)
        var phase = 0.0
        var lpNoise = 0f
        var hpPrev = 0f
        var prevRaw = 0f
        val rng = Random(seed)

        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            // 1. Sub-bass & chest punch sweep
            val freq = punchEndHz + (punchStartHz - punchEndHz) * exp(-28f * t)
            phase += 2.0 * PI * freq / sampleRate
            val punch = (sin(phase).toFloat() + 0.35f * sin(phase * 2.0).toFloat()) * exp(-punchDecay * t)

            // 2. Broadband muzzle crack + filtered gunpowder tail
            val white = rng.nextFloat() * 2f - 1f
            lpNoise = lpNoise * 0.72f + white * 0.28f
            val hpCrack = white - prevRaw * 0.85f
            prevRaw = white
            hpPrev = hpPrev * 0.5f + hpCrack * 0.5f

            val crack = hpPrev * exp(-95f * t) * crackLevel
            val tail = lpNoise * exp(-tailDecay * t) * 0.78f

            // 3. Mechanical slide/bolt click
            val mechT = t - (mechClickAtMs / 1000f)
            val mech = if (mechT >= 0f && mechT < 0.025f) {
                sin(2.0 * PI * 1950.0 * mechT).toFloat() * exp(-180f * mechT) * 0.25f
            } else {
                0f
            }

            val raw = (punch * 0.95f + crack + tail + mech) * bodyGain
            dry[i] = tanh(raw.toDouble()).toFloat()
        }

        // Add early chamber echo reflection for acoustic space
        val echoOffset = (sampleRate * echoDelayMs) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val echoed = dry[i] + if (i >= echoOffset) dry[i - echoOffset] * echoLevel else 0f
            val clamped = echoed.coerceIn(-1f, 1f)
            out[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthShotgunBlast(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        var lpNoise = 0f
        val rng = Random(303)

        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val freq = 42f + 115f * exp(-22f * t)
            phase += 2.0 * PI * freq / sampleRate
            val boom = sin(phase).toFloat() * exp(-15f * t) * 0.95f

            val white = rng.nextFloat() * 2f - 1f
            lpNoise = lpNoise * 0.62f + white * 0.38f
            val blast = (white * exp(-65f * t) * 0.9f) + (lpNoise * exp(-10f * t) * 0.85f)

            // Pump-action clack near tail
            val pumpT = t - 0.21f
            val pump = if (pumpT >= 0f && pumpT < 0.04f) {
                (sin(2.0 * PI * 980.0 * pumpT).toFloat() + white * 0.3f) * exp(-120f * pumpT) * 0.28f
            } else {
                0f
            }

            val mixed = tanh((boom + blast + pump).toDouble()).toFloat().coerceIn(-1f, 1f)
            out[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthRailgun(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        var p1 = 0.0
        var p2 = 0.0
        val rng = Random(505)

        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val prog = i.toFloat() / count
            val f1 = 2100f * exp(-6.5f * t) + 180f
            val f2 = 480f * (1f - prog * 0.72f)
            p1 += 2.0 * PI * f1 / sampleRate
            p2 += 2.0 * PI * f2 / sampleRate

            val noise = (rng.nextFloat() * 2f - 1f) * exp(-22f * t) * 0.55f
            val zap = sin(p1).toFloat() * 0.55f * exp(-11f * t)
            val sub = sin(p2).toFloat() * 0.65f * exp(-9f * t)
            val mixed = tanh((zap + sub + noise).toDouble()).toFloat().coerceIn(-1f, 1f)
            out[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthRicochet(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val freq = 1850f - 850f * (i.toFloat() / count) + 160f * sin(2.0 * PI * 45.0 * t).toFloat()
            phase += 2.0 * PI * freq / sampleRate
            val tone = sin(phase).toFloat() * exp(-22f * t) * 0.75f
            out[i] = (tone.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthGunBounce(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        val rng = Random(707)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val freq = 240f - 140f * (i.toFloat() / count)
            phase += 2.0 * PI * freq / sampleRate
            val clank = (sin(phase).toFloat() + 0.3f * (rng.nextFloat() * 2f - 1f)) * exp(-34f * t) * 0.75f
            out[i] = (clank.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthExplosion(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        var lp = 0f
        val rng = Random(808)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val subFreq = 130f * exp(-4.5f * t) + 28f
            phase += 2.0 * PI * subFreq / sampleRate
            val sub = sin(phase).toFloat() * exp(-6f * t) * 0.9f
            val white = rng.nextFloat() * 2f - 1f
            lp = lp * 0.84f + white * 0.16f
            val rumble = lp * exp(-5.5f * t) * 1.1f
            val crack = white * exp(-45f * t) * 0.7f
            val mixed = tanh((sub + rumble + crack).toDouble()).toFloat().coerceIn(-1f, 1f)
            out[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthArpeggio(notesHz: FloatArray, noteMs: Int, gain: Float): ShortArray {
        val noteSamples = (sampleRate * noteMs) / 1000
        val total = noteSamples * notesHz.size
        val out = ShortArray(total)
        for (nIdx in notesHz.indices) {
            val freq = notesHz[nIdx]
            var phase = 0.0
            for (i in 0 until noteSamples) {
                val t = i.toFloat() / sampleRate
                phase += 2.0 * PI * freq / sampleRate
                val env = exp(-10f * t) * (1f - exp(-200f * t))
                val tone = sin(phase).toFloat() + 0.35f * sin(phase * 2.0).toFloat()
                val sample = (tone * env * gain).coerceIn(-1f, 1f)
                out[nIdx * noteSamples + i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    private fun synthUiClick(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val freq = 1250f - 500f * (i.toFloat() / count)
            phase += 2.0 * PI * freq / sampleRate
            val sample = (sin(phase).toFloat() * exp(-70f * t) * 0.55f).coerceIn(-1f, 1f)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun writeWavFile(file: File, pcm: ShortArray) {
        val dataSize = pcm.size * 2
        val byteBuffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF header
        byteBuffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        byteBuffer.putInt(36 + dataSize)
        byteBuffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        // fmt chunk
        byteBuffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        byteBuffer.putInt(16) // PCM chunk size
        byteBuffer.putShort(1) // AudioFormat = 1 (PCM)
        byteBuffer.putShort(1) // NumChannels = 1 (Mono)
        byteBuffer.putInt(sampleRate)
        byteBuffer.putInt(sampleRate * 2) // ByteRate
        byteBuffer.putShort(2) // BlockAlign
        byteBuffer.putShort(16) // BitsPerSample
        // data chunk
        byteBuffer.put("data".toByteArray(Charsets.US_ASCII))
        byteBuffer.putInt(dataSize)
        for (s in pcm) {
            byteBuffer.putShort(s)
        }
        FileOutputStream(file).use { fos ->
            fos.write(byteBuffer.array())
        }
    }

    fun release() {
        scope.cancel()
        try {
            soundPool?.release()
        } catch (_: Throwable) {
        }
        soundPool = null
        soundIds.clear()
        loadedIds.clear()
    }
}
