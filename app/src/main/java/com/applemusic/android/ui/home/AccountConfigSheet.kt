package com.applemusic.android.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.applemusic.android.auth.AppleMusicCredentials
import com.applemusic.android.data.model.ConnectionMode
import com.applemusic.android.data.model.HomeItem
import com.applemusic.android.data.settings.AppFontOption
import com.applemusic.android.data.settings.AppLanguageOption
import com.applemusic.android.data.settings.AppSettingsUiState
import com.applemusic.android.data.settings.CacheLimitPreset
import com.applemusic.android.playback.AtmosPreferenceMode
import com.applemusic.android.playback.DeviceDolbyCapabilities
import java.util.Locale
import kotlin.math.roundToInt

private enum class SettingsSubPage {
    MAIN,
    HISTORY
}

/**
 * Hub completo de Cuenta y Configuración (Material 3 Expressive):
 * - Perfil real de usuario (`v1/me/social/profile`)
 * - Diseño (Fuentes + Idiomas soportados)
 * - Audio (Dolby Atmos No/Automático/Siempre, Ecualizador de bandas + BassBoost, Crossfade, AutoMix)
 * - Caché (desglose real en disco, límite configurable y limpieza)
 * - Historial de reproducciones y Apple Music Replay (Material 3 Expressive)
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun AccountConfigSheet(
    mode: ConnectionMode,
    credentials: AppleMusicCredentials,
    settings: AppSettingsUiState,
    atmosMode: AtmosPreferenceMode,
    dolbyCapabilities: DeviceDolbyCapabilities,
    onSetAtmosMode: (AtmosPreferenceMode) -> Unit,
    onSetFontOption: (AppFontOption) -> Unit,
    onSetLanguageOption: (AppLanguageOption) -> Unit,
    onSetCrossfadeSeconds: (Int) -> Unit,
    onSetAutoMixEnabled: (Boolean) -> Unit,
    onSetEqualizerEnabled: (Boolean) -> Unit,
    onApplyEqPreset: (Int) -> Unit,
    onSetEqBandLevel: (Int, Int) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onRefreshCacheStats: () -> Unit,
    onSetCacheLimitMb: (Int) -> Unit,
    onClearCaches: () -> Unit,
    loadListeningHistory: suspend () -> List<HomeItem>,
    onPlayHistoryItem: (HomeItem) -> Unit,
    onOpenReplay: () -> Unit,
    onDismiss: () -> Unit,
    onSignInWithAppleMusic: () -> Unit,
    onSignOut: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeSubPage by remember { mutableStateOf(SettingsSubPage.MAIN) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshCacheStats()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (activeSubPage == SettingsSubPage.HISTORY) {
            SettingsListeningHistoryPane(
                loadListeningHistory = loadListeningHistory,
                onBack = { activeSubPage = SettingsSubPage.MAIN },
                onPlayItem = { item ->
                    onPlayHistoryItem(item)
                    onDismiss()
                }
            )
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            val strings = com.applemusic.android.ui.theme.LocalAppStrings.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strings.settings,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                TextButton(onClick = onDismiss) {
                    Text(strings.done, fontWeight = FontWeight.Bold)
                }
            }

            // Encabezado con perfil real de Apple Music
            SettingsAccountHeaderCard(
                mode = mode,
                credentials = credentials,
                profile = settings.userProfile,
                onSignInWithAppleMusic = {
                    onDismiss()
                    onSignInWithAppleMusic()
                },
                onSignOut = {
                    onSignOut()
                    onDismiss()
                }
            )

            // Accesos Expressive rápidos: Apple Music Replay + Historial de reproducciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            onDismiss()
                            onOpenReplay()
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoGraph,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "Apple Music Replay",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = strings.replaySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { activeSubPage = SettingsSubPage.HISTORY }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = strings.history,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = strings.historySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // SECCIÓN 1: DISEÑO (Fuentes + Idiomas)
            SettingsSectionHeader(
                icon = Icons.Rounded.Palette,
                title = strings.design
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = strings.appFont,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppFontOption.entries.forEach { opt ->
                            val selected = opt == settings.fontOption
                            FilterChip(
                                selected = selected,
                                onClick = { onSetFontOption(opt) },
                                label = { Text(opt.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                shape = CircleShape
                            )
                        }
                    }
                    Text(
                        text = settings.fontOption.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { showLanguageMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.languageSubtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = settings.languageOption.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = strings.change,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            AppLanguageOption.entries.forEach { lang ->
                                val selected = lang == settings.languageOption
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = lang.label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    trailingIcon = if (selected) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    onClick = {
                                        showLanguageMenu = false
                                        onSetLanguageOption(lang)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // SECCIÓN 2: AUDIO (Dolby Atmos, Crossfade, AutoMix, Ecualizador)
            SettingsSectionHeader(
                icon = Icons.Rounded.GraphicEq,
                title = strings.audio
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dolby Atmos: No | Automático | Siempre
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.SurroundSound, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Dolby Atmos",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val options = listOf(
                                AtmosPreferenceMode.OFF to strings.atmosOff,
                                AtmosPreferenceMode.AUTOMATIC to strings.atmosAuto,
                                AtmosPreferenceMode.ALWAYS_ON to strings.atmosAlways
                            )
                            options.forEachIndexed { idx, (modeOpt, label) ->
                                SegmentedButton(
                                    selected = atmosMode == modeOpt,
                                    onClick = { onSetAtmosMode(modeOpt) },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size)
                                ) {
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Text(
                            text = if (dolbyCapabilities.isDolbyAtmosSupported) {
                                if (strings.isEnglish) {
                                    "Compatible hardware detected (${dolbyCapabilities.summaryLabel})."
                                } else {
                                    "Hardware compatible detectado (${dolbyCapabilities.summaryLabel})."
                                }
                            } else {
                                strings.atmosDescStandard
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Crossfade (0..12s)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Crossfade",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (settings.crossfadeSeconds == 0) strings.crossfadeOff else "${settings.crossfadeSeconds} s",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = settings.crossfadeSeconds.toFloat(),
                            onValueChange = { onSetCrossfadeSeconds(it.roundToInt()) },
                            valueRange = 0f..12f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = strings.crossfadeDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // AutoMix
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSetAutoMixEnabled(!settings.autoMixEnabled) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AutoMix",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.autoMixDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoMixEnabled,
                            onCheckedChange = onSetAutoMixEnabled
                        )
                    }

                    // Ecualizador real de sistema
                    val eq = settings.equalizer
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = strings.equalizer,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = eq.enabled,
                                onCheckedChange = onSetEqualizerEnabled,
                                enabled = eq.available
                            )
                        }

                        if (!eq.available) {
                            Text(
                                text = strings.equalizerUnavailable,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            AnimatedVisibility(visible = eq.enabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (eq.presetNames.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            eq.presetNames.forEachIndexed { idx, presetName ->
                                                FilterChip(
                                                    selected = eq.presetIndex == idx,
                                                    onClick = { onApplyEqPreset(idx) },
                                                    label = { Text(presetName) },
                                                    shape = CircleShape,
                                                    colors = FilterChipDefaults.filterChipColors()
                                                )
                                            }
                                        }
                                    }

                                    eq.bandLevelsMb.forEachIndexed { bandIdx, levelMb ->
                                        val hz = eq.bandCenterHz.getOrElse(bandIdx) { 0 }
                                        val freqLabel = if (hz >= 1000) {
                                            String.format(Locale.US, "%.1f kHz", hz / 1000f)
                                        } else {
                                            "$hz Hz"
                                        }
                                        val dbText = String.format(Locale.US, "%+.1f dB", levelMb / 100f)
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(freqLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                Text(dbText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Slider(
                                                value = levelMb.toFloat(),
                                                onValueChange = { onSetEqBandLevel(bandIdx, it.roundToInt()) },
                                                valueRange = eq.minLevelMb.toFloat()..eq.maxLevelMb.toFloat()
                                            )
                                        }
                                    }

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(strings.bassBoost, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            Text("${(eq.bassBoostStrength / 10)}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Slider(
                                            value = eq.bassBoostStrength.toFloat(),
                                            onValueChange = { onSetBassBoost(it.roundToInt()) },
                                            valueRange = 0f..1000f
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECCIÓN 3: CACHÉ Y ALMACENAMIENTO
            SettingsSectionHeader(
                icon = Icons.Rounded.Storage,
                title = strings.cache
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                val stats = settings.cacheStats
                val totalMb = stats.totalBytes / (1024f * 1024f)
                val limitMb = settings.cacheLimitMb.toFloat().coerceAtLeast(1f)
                val usageProgress = (totalMb / limitMb).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.usedSpace,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f MB / %d MB", totalMb, settings.cacheLimitMb),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearWavyProgressIndicator(
                        progress = { usageProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CacheBreakdownRow(strings.cacheArtwork, stats.artworkBytes)
                        CacheBreakdownRow(strings.cacheMotion, stats.motionVideoBytes)
                        CacheBreakdownRow(strings.cacheApi, stats.catalogHttpBytes + stats.jsonSnapshotBytes)
                        CacheBreakdownRow(strings.cacheOther, stats.otherCacheBytes)
                    }

                    Text(
                        text = strings.maxCacheLimit,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CacheLimitPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = settings.cacheLimitMb == preset.mb,
                                onClick = { onSetCacheLimitMb(preset.mb) },
                                label = { Text(preset.label) },
                                shape = CircleShape
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onClearCaches,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.clearCacheNow)
                    }
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape
            ) {
                Text(strings.close)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsAccountHeaderCard(
    mode: ConnectionMode,
    credentials: AppleMusicCredentials,
    profile: com.applemusic.android.data.settings.UserAccountProfile?,
    onSignInWithAppleMusic: () -> Unit,
    onSignOut: () -> Unit
) {
    val strings = com.applemusic.android.ui.theme.LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    if (!profile?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile?.avatarUrl,
                            contentDescription = profile?.name ?: "Perfil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = profile?.name?.takeIf { it.isNotBlank() } ?: when (mode) {
                            ConnectionMode.PERSONALIZED -> strings.accountTitle
                            ConnectionMode.CATALOG_ONLY -> strings.catalogMode
                            ConnectionMode.DEMO -> strings.offline
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!profile?.handle.isNullOrBlank()) {
                        Text(
                            text = "@${profile?.handle?.removePrefix("@")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = buildString {
                            append(
                                when {
                                    credentials.hasWebSession -> strings.fullSession
                                    credentials.hasUserToken -> "MusicKit"
                                    else -> strings.signInToSync
                                }
                            )
                            if (credentials.storefront.isNotBlank()) {
                                append(" · ")
                                append(credentials.storefront.uppercase())
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSignInWithAppleMusic,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (credentials.hasWebSession) strings.switchAccount else strings.signIn)
                }
                if (credentials.hasWebSession || credentials.hasUserToken) {
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.signOut)
                    }
                }
            }
        }
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun CacheBreakdownRow(label: String, bytes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f)),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsListeningHistoryPane(
    loadListeningHistory: suspend () -> List<HomeItem>,
    onBack: () -> Unit,
    onPlayItem: (HomeItem) -> Unit
) {
    var items by remember { mutableStateOf<List<HomeItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        items = loadListeningHistory()
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 680.dp)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver a Configuración")
            }
            Text(
                text = "Historial de reproducciones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text("Atrás") }
        }

        Spacer(Modifier.height(8.dp))

        when {
            loading -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Aún no hay reproducciones recientes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(items, key = { idx, it -> "${it.id}_$idx" }) { _, track ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onPlayItem(track) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(50.dp)
                            ) {
                                if (!track.artworkUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = track.artworkUrl,
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.MusicNote, contentDescription = null)
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (track.subtitle.isNotBlank()) {
                                    Text(
                                        text = track.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Reproducir",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
