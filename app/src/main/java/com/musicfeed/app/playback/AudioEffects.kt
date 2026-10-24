package com.musicfeed.app.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer

/**
 * Equalizer, bass boost, virtualizer and loudness, attached to the player's
 * audio session. Settings are saved and restored on every session.
 */
object AudioEffects {
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PRESET = "preset"
    private const val KEY_BASS = "bass"
    private const val KEY_VIRTUALIZER = "virtualizer"
    private const val KEY_LOUDNESS = "loudness"
    private fun bandKey(band: Int) = "band_$band"

    const val MAX_STRENGTH = 1000
    const val MAX_LOUDNESS_MB = 1500

    private var prefs: SharedPreferences? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    val isAvailable: Boolean get() = equalizer != null

    fun attach(context: Context, audioSessionId: Int) {
        release()
        val p = context.getSharedPreferences("audio_fx", Context.MODE_PRIVATE)
        prefs = p
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()

        equalizer?.let { eq ->
            val preset = p.getInt(KEY_PRESET, -1)
            if (preset in 0 until eq.numberOfPresets) {
                runCatching { eq.usePreset(preset.toShort()) }
            } else {
                for (band in 0 until eq.numberOfBands) {
                    if (p.contains(bandKey(band))) {
                        runCatching { eq.setBandLevel(band.toShort(), p.getInt(bandKey(band), 0).toShort()) }
                    }
                }
            }
        }
        applyBass(p.getInt(KEY_BASS, 0))
        applyVirtualizer(p.getInt(KEY_VIRTUALIZER, 0))
        applyLoudness(p.getInt(KEY_LOUDNESS, 0))
        applyEnabled(p.getBoolean(KEY_ENABLED, true))
    }

    fun release() {
        listOf(equalizer, bassBoost, virtualizer, loudness).forEach { runCatching { it?.release() } }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
    }

    var enabled: Boolean
        get() = prefs?.getBoolean(KEY_ENABLED, true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
            applyEnabled(value)
        }

    val bandCount: Int get() = equalizer?.numberOfBands?.toInt() ?: 0

    /** Band level range in millibels. */
    val levelRange: IntRange
        get() = equalizer?.bandLevelRange?.let { it[0].toInt()..it[1].toInt() } ?: -1500..1500

    fun centerFrequencyHz(band: Int): Int = (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000

    fun bandLevel(band: Int): Int =
        runCatching { equalizer?.getBandLevel(band.toShort())?.toInt() }.getOrNull() ?: 0

    fun setBandLevel(band: Int, level: Int) {
        runCatching { equalizer?.setBandLevel(band.toShort(), level.toShort()) }
        prefs?.edit()?.putInt(bandKey(band), level)?.putInt(KEY_PRESET, -1)?.apply()
    }

    val presets: List<String>
        get() = equalizer?.let { eq -> (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) } }
            ?: emptyList()

    val currentPreset: Int get() = prefs?.getInt(KEY_PRESET, -1) ?: -1

    fun usePreset(index: Int) {
        val eq = equalizer ?: return
        runCatching { eq.usePreset(index.toShort()) }
        val editor = prefs?.edit() ?: return
        editor.putInt(KEY_PRESET, index)
        for (band in 0 until eq.numberOfBands) editor.putInt(bandKey(band), bandLevel(band))
        editor.apply()
    }

    fun resetFlat() {
        for (band in 0 until bandCount) setBandLevel(band, 0)
    }

    var bass: Int
        get() = prefs?.getInt(KEY_BASS, 0) ?: 0
        set(value) {
            prefs?.edit()?.putInt(KEY_BASS, value)?.apply()
            applyBass(value)
        }

    var virtualizerStrength: Int
        get() = prefs?.getInt(KEY_VIRTUALIZER, 0) ?: 0
        set(value) {
            prefs?.edit()?.putInt(KEY_VIRTUALIZER, value)?.apply()
            applyVirtualizer(value)
        }

    /** Extra gain in millibels. */
    var loudnessGain: Int
        get() = prefs?.getInt(KEY_LOUDNESS, 0) ?: 0
        set(value) {
            prefs?.edit()?.putInt(KEY_LOUDNESS, value)?.apply()
            applyLoudness(value)
        }

    private fun applyEnabled(on: Boolean) {
        listOf(equalizer, bassBoost, virtualizer, loudness).forEach { runCatching { it?.setEnabled(on) } }
    }

    private fun applyBass(strength: Int) {
        runCatching { bassBoost?.setStrength(strength.coerceIn(0, MAX_STRENGTH).toShort()) }
    }

    private fun applyVirtualizer(strength: Int) {
        runCatching { virtualizer?.setStrength(strength.coerceIn(0, MAX_STRENGTH).toShort()) }
    }

    private fun applyLoudness(gainMb: Int) {
        runCatching { loudness?.setTargetGain(gainMb.coerceIn(0, MAX_LOUDNESS_MB)) }
    }
}
