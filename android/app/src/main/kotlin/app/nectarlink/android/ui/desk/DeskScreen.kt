// SPDX-License-Identifier: GPL-3.0-or-later
package app.nectarlink.android.ui.desk

import android.app.Activity
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nectarlink.android.R
import app.nectarlink.android.core.Core
import app.nectarlink.android.core.Device
import app.nectarlink.android.ui.remote.RemoteMode
import app.nectarlink.core.Link
import app.nectarlink.core.MediaAction
import app.nectarlink.core.PcStats
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DeskPanelMode(val label: String) {
    Overview("Ringkasan"),
    Monitor("Monitor PC"),
    DeckFocus("Deck & Fokus"),
}

/**
 * Full-screen PhoneBox-inspired Desk Panel (`docs/PLAN.md` §3.13):
 * keeps the display awake with subtle 1-minute OLED burn-in pixel shift,
 * large tabular clock & date, live PC system telemetry (CPU, RAM, Disk, Network, Temp, Uptime),
 * Now Playing media & audio controls, interactive Control Deck tiles, and Pomodoro focus timer.
 */
@Composable
fun DeskScreen(
    device: Device,
    core: Core,
    onOpenRemote: (RemoteMode) -> Unit,
    onOpenDeck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    app.nectarlink.android.ui.KeepScreenOn(
        app.nectarlink.android.ui.rememberCharging() || app.nectarlink.android.ui.rememberTouchedRecently(),
    )
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var minuteBucket by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            minuteBucket = now / 60_000L
            delay((60_000L - (now % 60_000L)).coerceIn(1_000L, 60_000L))
        }
    }

    val minutePhase = (minuteBucket % 12L).toDouble() * (Math.PI / 6.0)
    val shiftX = (sin(minutePhase) * 8.0).roundToInt()
    val shiftY = (cos(minutePhase) * 6.0).roundToInt()

    var panelMode by remember { mutableStateOf(DeskPanelMode.Overview) }
    val deckState = device.deckState
    val stats = deckState?.stats
    val summaries by core.pcMedia.summaries.collectAsStateWithLifecycle()
    val activeSummary = summaries[device.id]
    val artBitmap = remember(activeSummary?.artwork) { activeSummary?.artwork?.asImageBitmap() }

    var draggingVolume by remember { mutableStateOf<Float?>(null) }
    var lastSentVolInt by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    // Built-in Pomodoro Focus Timer state (PhoneBox widget)
    var focusRunning by remember { mutableStateOf(false) }
    var focusBreakMode by remember { mutableStateOf(false) }
    var focusSecondsLeft by remember { mutableIntStateOf(25 * 60) }
    LaunchedEffect(focusRunning) {
        while (focusRunning) {
            delay(1_000L)
            if (focusSecondsLeft > 0) {
                focusSecondsLeft--
            } else {
                focusBreakMode = !focusBreakMode
                focusSecondsLeft = if (focusBreakMode) 5 * 60 else 25 * 60
                focusRunning = false
            }
        }
    }

    Surface(
        color = Color.Black,
        contentColor = Color(0xFFEAECEF),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar: PC status + Panel Mode Switcher + Exit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (device.online) Color(0xFF0E2217) else Color(0xFF16181E),
                    border = BorderStroke(
                        1.dp,
                        if (device.online) Color(0xFF2EA05E) else Color(0xFF2B2F3A),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        val dotColor = if (device.online) Color(0xFF4ADE80) else Color(0xFF9CA3AF)
                        Spacer(
                            modifier = Modifier
                                .size(7.dp)
                                .drawBehind { drawCircle(dotColor) },
                        )
                        val linkDetail = when (val l = device.link) {
                            is Link.Online -> "${device.name} · ${l.rttMs} ms"
                            else -> "${device.name} · Offline"
                        }
                        Text(
                            text = linkDetail,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (device.online) Color(0xFFB8F5CD) else Color(0xFFD1D5DB),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onBack,
                    border = BorderStroke(1.dp, Color(0xFF282C37)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.desk_exit), color = Color(0xFFD1D5DB), fontSize = 12.sp)
                }
            }

            // Panel Switcher Pill (PhoneBox multi-page Desk Panel)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeskPanelMode.entries.forEach { mode ->
                    val selected = panelMode == mode
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF11141C),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else Color(0xFF222733),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .clickable { panelMode = mode },
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else Color(0xFF9CA3AF),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // Center Dynamic Panel Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (panelMode) {
                    DeskPanelMode.Overview -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .offset { IntOffset(shiftX, shiftY) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DeskClockDisplay()

                            // Live PhoneBox Telemetry Strip
                            if (device.online && stats != null) {
                                DeskStatsMiniStrip(stats = stats)
                            }

                            // PC Media & Audio Dock Card
                            if (device.online && (deckState != null || activeSummary != null)) {
                                val isPlaying = activeSummary?.playing ?: (deckState?.playing == true)
                                val outName = deckState?.outputDevices?.firstOrNull { it.isDefault }?.name
                                    ?: deckState?.outputDevices?.firstOrNull()?.name
                                    ?: device.name
                                val songTitle = activeSummary?.title?.takeIf { it.isNotBlank() }
                                    ?: if (isPlaying) stringResource(R.string.desk_media_playing) else stringResource(R.string.desk_media_audio)
                                val subtitleText = buildString {
                                    val sub = activeSummary?.subtitle?.trim().orEmpty()
                                    if (sub.isNotEmpty()) {
                                        append(sub)
                                        if (outName.isNotBlank() && !sub.contains(outName, ignoreCase = true)) {
                                            append(" · ")
                                            append(outName)
                                        }
                                    } else {
                                        append(outName)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color(0xFF0E1015),
                                    border = BorderStroke(1.dp, Color(0xFF222631)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (artBitmap != null) {
                                                Image(
                                                    bitmap = artBitmap,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .clip(RoundedCornerShape(12.dp)),
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_music),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Text(
                                                    text = songTitle,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (activeSummary != null) Color(0xFFF3F4F6) else MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = subtitleText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF9CA3AF),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        if (activeSummary != null) {
                                                            core.pcMedia.command(device.id, activeSummary.playerId, MediaAction.PREVIOUS)
                                                        } else {
                                                            scope.launch { core.deckPress(device.id, "media_previous") }
                                                        }
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = Color(0xFF1B1F2A),
                                                        contentColor = Color(0xFFE5E7EB),
                                                    ),
                                                    modifier = Modifier.size(36.dp),
                                                ) {
                                                    Icon(
                                                        painterResource(R.drawable.ic_skip_previous),
                                                        contentDescription = stringResource(R.string.media_previous),
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        if (activeSummary != null) {
                                                            core.pcMedia.command(
                                                                device.id,
                                                                activeSummary.playerId,
                                                                if (isPlaying) MediaAction.PAUSE else MediaAction.PLAY,
                                                            )
                                                        } else {
                                                            scope.launch { core.deckPress(device.id, "media_play_pause") }
                                                        }
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    ),
                                                    modifier = Modifier.size(40.dp),
                                                ) {
                                                    Icon(
                                                        painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                                        contentDescription = stringResource(R.string.deck_default_play_pause),
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        if (activeSummary != null) {
                                                            core.pcMedia.command(device.id, activeSummary.playerId, MediaAction.NEXT)
                                                        } else {
                                                            scope.launch { core.deckPress(device.id, "media_next") }
                                                        }
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = Color(0xFF1B1F2A),
                                                        contentColor = Color(0xFFE5E7EB),
                                                    ),
                                                    modifier = Modifier.size(36.dp),
                                                ) {
                                                    Icon(
                                                        painterResource(R.drawable.ic_skip_next),
                                                        contentDescription = stringResource(R.string.media_next),
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        }

                                        if (deckState != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                IconButton(
                                                    onClick = { core.setPcAudio(device.id, null, !deckState.muted) },
                                                    modifier = Modifier.size(32.dp),
                                                ) {
                                                    Icon(
                                                        painterResource(if (deckState.muted) R.drawable.ic_sound_off else R.drawable.ic_speaker),
                                                        contentDescription = null,
                                                        tint = Color(0xFFD1D5DB),
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                                val shownVol = (draggingVolume ?: deckState.volume.toFloat()).coerceIn(0f, 100f)
                                                Slider(
                                                    value = shownVol,
                                                    onValueChange = { v ->
                                                        draggingVolume = v
                                                        val rounded = v.roundToInt()
                                                        if (rounded != lastSentVolInt) {
                                                            lastSentVolInt = rounded
                                                            core.setPcAudio(device.id, rounded, null)
                                                        }
                                                    },
                                                    onValueChangeFinished = {
                                                        draggingVolume = null
                                                        lastSentVolInt = -1
                                                    },
                                                    valueRange = 0f..100f,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = MaterialTheme.colorScheme.primary,
                                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                                        inactiveTrackColor = Color(0xFF252A36),
                                                    ),
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Text(
                                                    text = if (deckState.muted) "Muted" else "${shownVol.roundToInt()}%",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                                    color = Color(0xFFD1D5DB),
                                                    modifier = Modifier.width(44.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (!device.online && device.canWake) {
                                FilledTonalButton(onClick = { core.wake(device.id) }) {
                                    Text(stringResource(R.string.action_wake_pc))
                                }
                            }
                        }
                    }

                    DeskPanelMode.Monitor -> {
                        DeskPcMonitorPanel(
                            deviceName = device.name,
                            online = device.online,
                            stats = stats,
                            deckState = deckState,
                            onToggleMic = {
                                scope.launch { core.deckPress(device.id, "mic_mute") }
                            },
                            onToggleMute = {
                                if (deckState != null) {
                                    core.setPcAudio(device.id, null, !deckState.muted)
                                }
                            },
                        )
                    }

                    DeskPanelMode.DeckFocus -> {
                        DeskDeckAndFocusPanel(
                            device = device,
                            focusRunning = focusRunning,
                            focusBreakMode = focusBreakMode,
                            focusSecondsLeft = focusSecondsLeft,
                            onToggleFocus = { focusRunning = !focusRunning },
                            onSwitchFocusPhase = {
                                focusRunning = false
                                focusBreakMode = !focusBreakMode
                                focusSecondsLeft = if (focusBreakMode) 5 * 60 else 25 * 60
                            },
                            onResetFocus = {
                                focusRunning = false
                                focusSecondsLeft = if (focusBreakMode) 5 * 60 else 25 * 60
                            },
                            onPressTile = { tileId ->
                                scope.launch { core.deckPress(device.id, tileId) }
                            },
                            onOpenFullDeck = onOpenDeck,
                        )
                    }
                }
            }

            // Bottom Dock Bar: Symmetric 2×2 Grid of Quick Action Pills
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DeskActionButton(
                        label = stringResource(R.string.desk_pc_screen),
                        enabled = device.online,
                        onClick = { onOpenRemote(RemoteMode.Screen) },
                        modifier = Modifier.weight(1f),
                    )
                    DeskActionButton(
                        label = stringResource(R.string.action_remote_pc),
                        enabled = device.online,
                        onClick = { onOpenRemote(RemoteMode.Touchpad) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DeskActionButton(
                        label = stringResource(R.string.action_deck_pc),
                        enabled = device.online,
                        onClick = onOpenDeck,
                        modifier = Modifier.weight(1f),
                    )
                    DeskActionButton(
                        label = stringResource(R.string.action_lock_pc),
                        enabled = device.online,
                        isSecondary = true,
                        onClick = { core.pcPower(device.id, sleep = false) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeskStatsMiniStrip(stats: PcStats) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0E1118),
        border = BorderStroke(1.dp, Color(0xFF202634)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniStatBadge("CPU", "${stats.cpuPercent}%", stats.cpuPercent.toInt() >= 85)
            MiniStatBadge("RAM", "${stats.ramPercent}%", stats.ramPercent.toInt() >= 85)
            MiniStatBadge("DISK", "${stats.diskPercent}%", stats.diskPercent.toInt() >= 90)
            stats.tempC?.let { temp ->
                MiniStatBadge("SUHU", "${temp}°C", temp.toInt() >= 82)
            }
            MiniStatBadge("NET", "↓${formatNetRate(stats.netRxKbps)}", false)
        }
    }
}

@Composable
private fun MiniStatBadge(label: String, value: String, warn: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color(0xFF9CA3AF), fontWeight = FontWeight.Medium)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (warn) Color(0xFFF87171) else Color(0xFFF3F4F6),
        )
    }
}

@Composable
private fun DeskPcMonitorPanel(
    deviceName: String,
    online: Boolean,
    stats: PcStats?,
    deckState: app.nectarlink.core.DeckState?,
    onToggleMic: () -> Unit,
    onToggleMute: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!online || stats == null) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0E1015),
                border = BorderStroke(1.dp, Color(0xFF222631)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (!online) "$deviceName sedang luring." else "Menunggu telemetri sistem dari $deviceName…",
                    color = Color(0xFF9CA3AF),
                    modifier = Modifier.padding(18.dp),
                )
            }
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatGaugeCard(
                title = "CPU",
                valueText = "${stats.cpuPercent}%",
                subtitle = stats.tempC?.let { "Suhu ${it}°C" } ?: "Prosesor aktif",
                fraction = stats.cpuPercent.toFloat() / 100f,
                accent = if (stats.cpuPercent.toInt() >= 85) Color(0xFFEF4444) else Color(0xFF3B82F6),
                modifier = Modifier.weight(1f),
            )
            StatGaugeCard(
                title = "Memori (RAM)",
                valueText = "${stats.ramPercent}%",
                subtitle = String.format("%.1f / %.1f GB", stats.ramUsedMb.toFloat() / 1024f, stats.ramTotalMb.toFloat() / 1024f),
                fraction = stats.ramPercent.toFloat() / 100f,
                accent = if (stats.ramPercent.toInt() >= 85) Color(0xFFF59E0B) else Color(0xFF10B981),
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatGaugeCard(
                title = "Penyimpanan (Disk)",
                valueText = "${stats.diskPercent}%",
                subtitle = "${stats.diskUsedGb} / ${stats.diskTotalGb} GB",
                fraction = stats.diskPercent.toFloat() / 100f,
                accent = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0E1118),
                border = BorderStroke(1.dp, Color(0xFF202634)),
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Jaringan & Uptime", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    Text(
                        text = "↓ ${formatNetRate(stats.netRxKbps)}  ↑ ${formatNetRate(stats.netTxKbps)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF60A5FA),
                        maxLines = 1,
                    )
                    Text(
                        text = "Nyala: ${formatUptime(stats.uptimeSecs)}",
                        fontSize = 12.sp,
                        color = Color(0xFFD1D5DB),
                    )
                }
            }
        }

        // Quick Audio Hardware Status Row
        if (deckState != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DeskActionButton(
                    label = if (deckState.muted) "Speaker: Bisu" else "Speaker: ${deckState.volume}%",
                    enabled = true,
                    isSecondary = !deckState.muted,
                    onClick = onToggleMute,
                    modifier = Modifier.weight(1f),
                )
                DeskActionButton(
                    label = if (deckState.micMuted == true) "Mikrofon: Mati" else "Mikrofon: Aktif",
                    enabled = true,
                    isSecondary = deckState.micMuted != true,
                    onClick = onToggleMic,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatGaugeCard(
    title: String,
    valueText: String,
    subtitle: String,
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0E1118),
        border = BorderStroke(1.dp, Color(0xFF202634)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 12.sp, color = Color(0xFF9CA3AF))
                Text(
                    valueText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accent,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1E2430)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(accent),
                )
            }
            Text(subtitle, fontSize = 11.5.sp, color = Color(0xFFD1D5DB), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeskDeckAndFocusPanel(
    device: Device,
    focusRunning: Boolean,
    focusBreakMode: Boolean,
    focusSecondsLeft: Int,
    onToggleFocus: () -> Unit,
    onSwitchFocusPhase: () -> Unit,
    onResetFocus: () -> Unit,
    onPressTile: (String) -> Unit,
    onOpenFullDeck: () -> Unit,
) {
    val pages = device.deckLayout?.pages.orEmpty()
    val firstTiles = pages.firstOrNull()?.tiles.orEmpty().take(6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Pomodoro / Focus Timer Card (PhoneBox productivity widget)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0E1118),
            border = BorderStroke(1.dp, Color(0xFF202634)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (focusBreakMode) "Timer Istirahat" else "Timer Fokus Pomodoro",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF),
                    )
                    val mins = focusSecondsLeft / 60
                    val secs = focusSecondsLeft % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (focusRunning) Color(0xFF4ADE80) else Color(0xFFF3F4F6),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onToggleFocus) {
                        Text(if (focusRunning) "Jeda" else "Mulai")
                    }
                    OutlinedButton(
                        onClick = onSwitchFocusPhase,
                        border = BorderStroke(1.dp, Color(0xFF2B3140)),
                    ) {
                        Text(if (focusBreakMode) "Fokus" else "Rehat", color = Color(0xFFD1D5DB))
                    }
                    OutlinedButton(
                        onClick = onResetFocus,
                        border = BorderStroke(1.dp, Color(0xFF2B3140)),
                    ) {
                        Text("Reset", color = Color(0xFFD1D5DB))
                    }
                }
            }
        }

        // Control Deck Quick Tiles right in Desk Mode
        if (firstTiles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                firstTiles.chunked(2).forEach { rowTiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowTiles.forEach { tile ->
                            DeskActionButton(
                                label = tile.label,
                                enabled = device.online,
                                onClick = { onPressTile(tile.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowTiles.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            DeskActionButton(
                label = "Buka Editor Control Deck",
                enabled = device.online,
                onClick = onOpenFullDeck,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatNetRate(kbps: UInt): String {
    val v = kbps.toInt()
    return if (v >= 1024) {
        String.format("%.1f MB/s", v / 1024f)
    } else {
        "$v KB/s"
    }
}

private fun formatUptime(secs: ULong): String {
    val totalMins = secs.toLong() / 60L
    val hours = totalMins / 60L
    val mins = totalMins % 60L
    val days = hours / 24L
    return when {
        days > 0 -> "${days}h ${hours % 24}j"
        hours > 0 -> "${hours}j ${mins}m"
        else -> "${mins}m"
    }
}

@Composable
private fun DeskActionButton(
    label: String,
    enabled: Boolean,
    isSecondary: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isSecondary) Color(0xFF12141B) else Color(0xFF171B24),
            contentColor = if (isSecondary) Color(0xFFD1D5DB) else Color(0xFFF3F4F6),
            disabledContainerColor = Color(0xFF0C0E12),
            disabledContentColor = Color(0xFF4B5563),
        ),
        border = BorderStroke(
            1.dp,
            if (isSecondary) Color(0xFF262A36) else Color(0xFF2B3140),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeskClockDisplay() {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            nowMs = current
            delay((1_000L - (current % 1_000L)).coerceIn(100L, 1_000L))
        }
    }

    val locale = LocalLocale.current.platformLocale
    val timeFormat = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val secFormat = remember(locale) { SimpleDateFormat(":ss", locale) }
    val dateFormat = remember(locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd")
        SimpleDateFormat(pattern, locale)
    }

    val minuteKey = nowMs / 60_000L
    val secondKey = nowMs / 1_000L
    val timeText = remember(minuteKey, locale) { timeFormat.format(Date(minuteKey * 60_000L)) }
    val dateText = remember(minuteKey, locale) { dateFormat.format(Date(minuteKey * 60_000L)) }
    val secText = remember(secondKey, locale) { secFormat.format(Date(secondKey * 1_000L)) }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = timeText,
            fontSize = 72.sp,
            lineHeight = 76.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFF3F4F6),
        )
        Text(
            text = secText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF9CA3AF),
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
        )
    }

    Text(
        text = dateText,
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFF9CA3AF),
    )
}
