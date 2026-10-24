package com.musicfeed.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicfeed.app.playback.AudioEffects
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (!AudioEffects.isAvailable) {
            Text(
                "Audio effects aren't available yet. Start playback first, or your device may not support them.",
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            return@Scaffold
        }
        EqualizerControls(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun EqualizerControls(modifier: Modifier) {
    var enabled by remember { mutableStateOf(AudioEffects.enabled) }
    var preset by remember { mutableIntStateOf(AudioEffects.currentPreset) }
    val range = AudioEffects.levelRange
    val levels = remember {
        mutableStateListOf<Int>().apply { repeat(AudioEffects.bandCount) { add(AudioEffects.bandLevel(it)) } }
    }
    fun reloadLevels() {
        for (band in levels.indices) levels[band] = AudioEffects.bandLevel(band)
    }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Enable effects", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                AudioEffects.enabled = it
            })
        }

        val presets = remember { AudioEffects.presets }
        if (presets.isNotEmpty()) {
            Text("Presets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(presets) { index, name ->
                    FilterChip(
                        selected = preset == index,
                        enabled = enabled,
                        onClick = {
                            preset = index
                            AudioEffects.usePreset(index)
                            reloadLevels()
                        },
                        label = { Text(name) },
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Bands", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(enabled = enabled, onClick = {
                AudioEffects.resetFlat()
                preset = -1
                reloadLevels()
            }) { Text("Flat") }
        }
        levels.forEachIndexed { band, level ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatFrequency(AudioEffects.centerFrequencyHz(band)), Modifier.width(64.dp))
                Slider(
                    value = level.toFloat(),
                    onValueChange = {
                        levels[band] = it.roundToInt()
                        preset = -1
                        AudioEffects.setBandLevel(band, levels[band])
                    },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                Text("%+.1f dB".format(level / 100f), Modifier.width(72.dp), style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        EffectSlider("Bass boost", AudioEffects.bass, AudioEffects.MAX_STRENGTH, enabled) {
            AudioEffects.bass = it
        }
        EffectSlider("Surround (virtualizer)", AudioEffects.virtualizerStrength, AudioEffects.MAX_STRENGTH, enabled) {
            AudioEffects.virtualizerStrength = it
        }
        EffectSlider("Loudness", AudioEffects.loudnessGain, AudioEffects.MAX_LOUDNESS_MB, enabled) {
            AudioEffects.loudnessGain = it
        }
    }
}

@Composable
private fun EffectSlider(label: String, initial: Int, max: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var value by remember { mutableFloatStateOf(initial.toFloat()) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${(value / max * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = {
                value = it
                onChange(it.roundToInt())
            },
            valueRange = 0f..max.toFloat(),
            enabled = enabled,
        )
    }
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) "%.1f kHz".format(hz / 1000f).replace(".0 ", " ") else "$hz Hz"
