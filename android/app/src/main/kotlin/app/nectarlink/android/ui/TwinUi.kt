// SPDX-License-Identifier: GPL-3.0-or-later
package app.nectarlink.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nectarlink.android.R
import app.nectarlink.android.clipboard.SendActivity
import app.nectarlink.android.core.CoreState
import app.nectarlink.android.core.Device
import app.nectarlink.android.ui.remote.RemoteMode
import app.nectarlink.core.Link

/**
 * TwinDesk-inspired Material 3 Expressive visual palette for LinkHub Mobile:
 * accent-tinted outer chrome, big rounded elevated sheet, borderless shadowed cards,
 * tonal inner chips, colored gradient squircle tiles, and floating pill navigation.
 */
@Immutable
data class TwinPalette(
    val accent: Color,
    val accentSoft: Color,
    val outer: Color,
    val sheet: Color,
    val card: Color,
    val chip: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val dark: Boolean,
)

val LocalTwinPalette = staticCompositionLocalOf {
    buildTwinPalette(accent = Color(0xFF2563EB), dark = false)
}

fun buildTwinPalette(accent: Color, dark: Boolean): TwinPalette {
    val up = { c: Color, f: Float -> lerp(c, Color.White, f) }
    val outer = lerp(if (dark) Color(0xFF0B0B0E) else Color(0xFFEDEDF2), accent, if (dark) 0.10f else 0.09f)
    val sheet = if (dark) up(outer, 0.05f) else up(outer, 0.65f)
    val card = if (dark) up(sheet, 0.06f) else Color.White
    val chip = if (dark) up(card, 0.07f) else lerp(sheet, Color.Black, 0.035f)
    return TwinPalette(
        accent = accent,
        accentSoft = accent.copy(alpha = if (dark) 0.24f else 0.13f),
        outer = outer,
        sheet = sheet,
        card = card,
        chip = chip,
        line = if (dark) Color(0x16FFFFFF) else Color(0x0F000000),
        text = if (dark) Color(0xFFF5F5F7) else Color(0xFF1D1D1F),
        muted = if (dark) Color(0xFF98989D) else Color(0xFF6E6E73),
        faint = if (dark) Color(0xFF6C6C70) else Color(0xFFA1A1A6),
        good = if (dark) Color(0xFF32D74B) else Color(0xFF30B94D),
        warn = if (dark) Color(0xFFFFB340) else Color(0xFFE8A10C),
        bad = if (dark) Color(0xFFFF453A) else Color(0xFFE5373A),
        dark = dark,
    )
}

@Composable
fun ProvideTwinPalette(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val palette = remember(scheme.primary, dark) { buildTwinPalette(scheme.primary, dark) }
    CompositionLocalProvider(LocalTwinPalette provides palette, content = content)
}

// ---- Crisp 20×20 line icons (TwinDesk aesthetic) ----

private fun twinLineIcon(name: String, d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 20f, 20f).apply {
        addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.65f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

object TwinIcons {
    val home = twinLineIcon("home", "M3.5 9.5L10 3.5l6.5 6v7a1 1 0 0 1-1 1h-3.8v-4.5H8.3v4.5H4.5a1 1 0 0 1-1-1z")
    val screen = twinLineIcon("screen", "M2.5 4h15v9.5h-15z M7 16.5h6 M10 13.5v3")
    val touchpad = twinLineIcon("touchpad", "M3 4h14v9.5H3z M3 13.5h14v2.5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z M10 13.5V17")
    val air = twinLineIcon("air", "M7 6.5h4.5v10H7z M8.5 14.5h1.5 M13.5 5.5a3 3 0 0 1 0 4 M15.5 3.5a6 6 0 0 1 0 8")
    val present = twinLineIcon("present", "M2.5 3.5h15v9.5h-15z M10 13v3.5 M7 17h6 M6 10l3-3 2 2 3-3")
    val files = twinLineIcon("files", "M2.5 5h5.5l1.5 2h8v9.5h-15z")
    val send = twinLineIcon("send", "M3 5h5l1.5 2H17v9H3z M10 14.5v-5 M7.8 11.5L10 9.3l2.2 2.2")
    val clipboard = twinLineIcon("clipboard", "M7 3.5h6v2.5H7z M5 5h2 M13 5h2a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1")
    val deck = twinLineIcon("deck", "M3.5 3.5h5v5h-5z M11.5 3.5h5v5h-5z M3.5 11.5h5v5h-5z M11.5 11.5h5v5h-5z")
    val camera = twinLineIcon("camera", "M2.5 6h10v8h-10z M12.5 8.5l5-2.5v8l-5-2.5")
    val mic = twinLineIcon("mic", "M10 2.5a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1-5 0V5A2.5 2.5 0 0 1 10 2.5z M5 9.5a5 5 0 0 0 10 0 M10 14.5v3 M7.5 17.5h5")
    val clock = twinLineIcon("clock", "M10 2.8a7.2 7.2 0 1 0 0 14.4a7.2 7.2 0 1 0 0-14.4z M10 6v4l2.8 1.8")
    val bell = twinLineIcon("bell", "M5.5 13.5V9a4.5 4.5 0 0 1 9 0v4.5l1.5 1.5h-12z M8.5 16.5a1.5 1.5 0 0 0 3 0")
    val lock = twinLineIcon("lock", "M5 9h10v8H5z M7 9V6.5a3 3 0 0 1 6 0V9 M10 12v2")
    val sleep = twinLineIcon("sleep", "M15.5 12.5A6.5 6.5 0 0 1 7.5 4.5a6.5 6.5 0 1 0 8 8z")
    val more = twinLineIcon("more", "M4.5 10h.1 M10 10h.1 M15.5 10h.1")
    val settings = twinLineIcon("settings", "M10 7a3 3 0 1 0 0 6a3 3 0 1 0 0-6z M10 2.5v2 M10 15.5v2 M2.5 10h2 M15.5 10h2 M4.7 4.7l1.4 1.4 M13.9 13.9l1.4 1.4 M4.7 15.3l1.4-1.4 M13.9 6.1l1.4-1.4")
    val guide = twinLineIcon("guide", "M10 2.5a7.5 7.5 0 1 0 0 15a7.5 7.5 0 1 0 0-15z M10 9v5 M10 6.2v.1")
    val qr = twinLineIcon("qr", "M3.5 3.5h4.5v4.5H3.5z M12 3.5h4.5v4.5H12z M3.5 12h4.5v4.5H3.5z M12 12h2v2h-2z M15.5 12v4.5H12")
    val refresh = twinLineIcon("refresh", "M15.5 10a5.5 5.5 0 1 1-1.6-3.9 M15.5 4v3h-3")
    val chevron = twinLineIcon("chevron", "M7.5 4.5L13 10l-5.5 5.5")
    val back = twinLineIcon("back", "M12.5 4.5L7 10l5.5 5.5")
    val plus = twinLineIcon("plus", "M10 4.5v11 M4.5 10h11")
    val wireless = twinLineIcon("wireless", "M3.5 7.5a9.2 9.2 0 0 1 13 0 M6 10.5a5.7 5.7 0 0 1 8 0 M8.5 13.5a2.2 2.2 0 0 1 3 0 M10 16v.1")
    val phone = twinLineIcon("phone", "M6.5 2.5h7a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-7a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1z M9 15h2")
    val mesh = twinLineIcon("mesh", "M5 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z M15 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z M10 13a2 2 0 1 0 0 4a2 2 0 1 0 0-4z M7 7h6 M6 8.8l3 4.4 M14 8.8l-3 4.4")
}

object TwinTileGradients {
    val blue = listOf(Color(0xFF2563EB), Color(0xFF38BDF8))
    val indigo = listOf(Color(0xFF4F46E5), Color(0xFF818CF8))
    val purple = listOf(Color(0xFF7C3AED), Color(0xFFC084FC))
    val emerald = listOf(Color(0xFF059669), Color(0xFF34D399))
    val amber = listOf(Color(0xFFD97706), Color(0xFFFBBF24))
    val rose = listOf(Color(0xFFE11D48), Color(0xFFFB7185))
    val cyan = listOf(Color(0xFF0891B2), Color(0xFF22D3EE))
    val slate = listOf(Color(0xFF475569), Color(0xFF94A3B8))
}

@Composable
fun TwinDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun TwinTile(icon: ImageVector, colors: List<Color>, size: Dp = 32.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.verticalGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun TwinCard(
    title: String = "",
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pal = LocalTwinPalette.current
    Column(modifier.padding(bottom = 16.dp)) {
        if (action != null || title.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title.isNotEmpty()) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = pal.text,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (action != null) action()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color.Black.copy(alpha = if (pal.dark) 0.25f else 0.06f),
                    spotColor = Color.Black.copy(alpha = if (pal.dark) 0.25f else 0.06f),
                )
                .clip(RoundedCornerShape(22.dp))
                .background(pal.card),
            content = content,
        )
    }
}

@Composable
fun TwinRow(
    label: String,
    hint: String = "",
    first: Boolean = false,
    icon: ImageVector? = null,
    tileColors: List<Color> = TwinTileGradients.blue,
    activeDot: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val pal = LocalTwinPalette.current
    Column(Modifier.alpha(if (enabled) 1f else 0.45f)) {
        if (!first) {
            Box(
                Modifier
                    .padding(start = if (icon != null) 62.dp else 20.dp, end = 20.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(pal.line),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                TwinTile(icon, tileColors, size = 32.dp)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = pal.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hint.isNotEmpty()) {
                    Text(
                        text = hint,
                        color = pal.muted,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (activeDot) {
                Spacer(Modifier.width(8.dp))
                TwinDot(pal.good, 8.dp)
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = trailing,
                )
            } else if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = TwinIcons.chevron,
                    contentDescription = null,
                    tint = pal.faint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun TwinPageHead(title: String, subtitle: String = "", leading: (@Composable () -> Unit)? = null) {
    val pal = LocalTwinPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = pal.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 13.5.sp,
                    color = pal.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun TwinPillButton(
    label: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalTwinPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) pal.accent else pal.chip,
            contentColor = if (primary) Color.White else pal.text,
            disabledContainerColor = pal.chip.copy(alpha = 0.5f),
            disabledContentColor = pal.faint,
        ),
        modifier = modifier.height(40.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * TwinDesk-style top branding bar resting on the tinted outer chrome above the rounded sheet.
 */
@Composable
fun TwinTopIdentity(
    state: CoreState,
    onRefresh: () -> Unit,
    onPairNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalTwinPalette.current
    val primaryOnline = state.devices.firstOrNull { it.online }
    val connecting = state.devices.any { it.link is Link.Connecting }
    val statusColor = when {
        primaryOnline != null -> pal.good
        connecting -> pal.warn
        else -> pal.faint
    }
    val statusText = when {
        primaryOnline != null -> {
            val link = primaryOnline.link as? Link.Online
            val via = when {
                link?.usb == true -> stringResource(R.string.link_usb)
                link?.relayed == true -> stringResource(R.string.link_away)
                else -> "Wi-Fi"
            }
            val rtt = link?.rttMs?.takeIf { it > 0u }?.let { " · ${it} ms" }.orEmpty()
            "${primaryOnline.name} · $via$rtt"
        }
        connecting -> stringResource(R.string.link_connecting)
        state.devices.isNotEmpty() -> "${state.devices.first().name} · ${stringResource(R.string.not_connected)}"
        else -> stringResource(R.string.not_connected)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(4.dp, RoundedCornerShape(13.dp), ambientColor = Color.Black.copy(alpha = 0.14f))
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF111827)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_linkhub_fg),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = pal.text,
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(pal.accentSoft)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "v${app.nectarlink.android.BuildConfig.VERSION_NAME}",
                        color = pal.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TwinDot(statusColor, 7.dp)
                Text(
                    text = statusText,
                    color = pal.muted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f))
                .clip(CircleShape)
                .background(pal.card)
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TwinIcons.refresh, contentDescription = stringResource(R.string.action_refresh), tint = pal.text, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f))
                .clip(CircleShape)
                .background(pal.accent)
                .clickable(onClick = onPairNew),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TwinIcons.qr, contentDescription = stringResource(R.string.action_pair_new), tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
}

internal data class TwinBottomDest(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val dot: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * TwinDesk's signature floating pill bottom bar (BottomBar.kt aesthetic):
 * floats above navigation insets with 28dp corners, soft shadow, 56×32dp accent pill
 * behind the active line icon, green live dot, and crisp single-line labels.
 */
@Composable
internal fun TwinFloatingBottomBar(
    dests: List<TwinBottomDest>,
    modifier: Modifier = Modifier,
) {
    val pal = LocalTwinPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp, top = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color.Black.copy(alpha = if (pal.dark) 0.35f else 0.12f),
                    spotColor = Color.Black.copy(alpha = if (pal.dark) 0.35f else 0.12f),
                )
                .clip(RoundedCornerShape(28.dp))
                .background(pal.card)
                .selectableGroup()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dests.forEach { dest ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(role = Role.Tab, onClick = dest.onClick)
                        .semantics { selected = dest.selected }
                        .padding(top = 10.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .width(46.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dest.selected) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(50))
                                    .background(pal.accentSoft),
                            )
                        }
                        Box {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = null,
                                tint = if (dest.selected) pal.accent else pal.muted,
                                modifier = Modifier.size(21.dp),
                            )
                            if (dest.dot) {
                                Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)) {
                                    TwinDot(pal.good, 7.dp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = dest.label,
                        color = if (dest.selected) pal.text else pal.muted,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = if (dest.selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Tablet sidebar (width >= 600dp), matching TwinDesk's desktop/tablet split chrome.
 */
@Composable
internal fun TwinTabletSidebar(
    state: CoreState,
    items: List<Triple<TwinBottomDest, List<Color>, String>>,
    onPairNew: () -> Unit,
    onRefresh: () -> Unit,
) {
    val pal = LocalTwinPalette.current
    Column(
        modifier = Modifier
            .width(250.dp)
            .fillMaxHeight()
            .background(pal.outer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        TwinTopIdentity(state = state, onRefresh = onRefresh, onPairNew = onPairNew, modifier = Modifier.padding(bottom = 12.dp))
        items.forEach { (dest, colors, _) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (dest.selected) pal.accentSoft else Color.Transparent)
                    .clickable(onClick = dest.onClick)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TwinTile(dest.icon, colors, size = 28.dp)
                Text(
                    text = dest.label,
                    color = pal.text,
                    fontSize = 15.sp,
                    fontWeight = if (dest.selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                )
                if (dest.dot) TwinDot(pal.good, 7.dp)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * TwinDesk-style Screen & Input Hub tab: quick 1-tap access to PC Screen Mirror,
 * Touchpad & Keyboard, Air Mouse, Presentation Laser, and Desk Mode, with multi-PC mesh switcher.
 */
@Composable
fun TwinScreenControlTab(
    state: CoreState,
    onOpenRemote: (pcId: String, mode: RemoteMode) -> Unit,
    onOpenDesk: (pcId: String) -> Unit,
    onPairNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalTwinPalette.current
    val pcs = state.devices.filter { !it.isAndroid }
    var chosenPcId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPc = pcs.firstOrNull { it.id == chosenPcId }
        ?: pcs.firstOrNull { it.online }
        ?: pcs.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 104.dp),
    ) {
        TwinPageHead(
            title = stringResource(R.string.remote_mode_screen),
            subtitle = selectedPc?.let {
                if (it.online) "${it.name} · ${stringResource(R.string.connected)}"
                else "${it.name} · ${stringResource(R.string.not_connected)}"
            } ?: stringResource(R.string.not_connected),
        )

        if (selectedPc == null) {
            TwinCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.not_connected), color = pal.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(stringResource(R.string.pair_scan_hint), color = pal.muted, fontSize = 13.5.sp)
                    TwinPillButton(label = stringResource(R.string.action_pair_new), primary = true, onClick = onPairNew)
                }
            }
            return
        }

        if (pcs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pcs.forEach { pc ->
                    val isSelected = pc.id == selectedPc.id
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) pal.accent else pal.card)
                            .clickable { chosenPcId = pc.id }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        TwinDot(if (pc.online) (if (isSelected) Color.White else pal.good) else pal.faint, 7.dp)
                        Text(
                            pc.name,
                            color = if (isSelected) Color.White else pal.text,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        TwinCard(title = selectedPc.name) {
            TwinRow(
                label = stringResource(R.string.remote_mode_screen),
                hint = stringResource(R.string.action_remote_pc),
                first = true,
                icon = TwinIcons.screen,
                tileColors = TwinTileGradients.blue,
                activeDot = selectedPc.online,
                onClick = { onOpenRemote(selectedPc.id, RemoteMode.Screen) },
            )
            TwinRow(
                label = stringResource(R.string.remote_mode_touchpad),
                hint = stringResource(R.string.remote_touchpad_hint),
                icon = TwinIcons.touchpad,
                tileColors = TwinTileGradients.indigo,
                onClick = { onOpenRemote(selectedPc.id, RemoteMode.Touchpad) },
            )
            TwinRow(
                label = stringResource(R.string.remote_mode_air),
                hint = stringResource(R.string.remote_air_hint),
                icon = TwinIcons.air,
                tileColors = TwinTileGradients.cyan,
                onClick = { onOpenRemote(selectedPc.id, RemoteMode.AirMouse) },
            )
            TwinRow(
                label = stringResource(R.string.remote_mode_presentation),
                hint = stringResource(R.string.remote_mode_presentation),
                icon = TwinIcons.present,
                tileColors = TwinTileGradients.amber,
                onClick = { onOpenRemote(selectedPc.id, RemoteMode.Presentation) },
            )
            TwinRow(
                label = stringResource(R.string.action_desk_pc),
                hint = selectedPc.name,
                icon = TwinIcons.clock,
                tileColors = TwinTileGradients.purple,
                onClick = { onOpenDesk(selectedPc.id) },
            )
        }
    }
}

private const val MESH_TARGET_ALL_ONLINE = "__mesh_all_online__"

/**
 * TwinDesk-style Mesh Ecosystem Share & Transfers Hub tab:
 * Lets the user choose ANY paired device in the mesh (PC, Laptop, Phone, Tablet)
 * or broadcast to all online devices at once for Send Files, Send Folder, and Send Clipboard.
 */
@Composable
fun TwinFilesShareTab(
    state: CoreState,
    onSendFiles: (pcId: String, uris: List<Uri>) -> Unit,
    onSendFolder: (pcId: String, tree: Uri) -> Unit,
    onRing: (id: String, on: Boolean) -> Unit,
    onPower: (pcId: String, sleep: Boolean) -> Unit,
    onPairNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalTwinPalette.current
    val context = LocalContext.current
    val allDevices = state.devices
    val onlineDevices = allDevices.filter { it.online }
    var chosenTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val sendToAll = chosenTargetId == MESH_TARGET_ALL_ONLINE && onlineDevices.size >= 2
    val target: Device? = if (sendToAll) {
        null
    } else {
        allDevices.firstOrNull { it.id == chosenTargetId }
            ?: allDevices.firstOrNull { it.online }
            ?: allDevices.firstOrNull()
    }
    var pendingPickerDeviceId by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val explicit = pendingPickerDeviceId
        pendingPickerDeviceId = null
        when {
            explicit == MESH_TARGET_ALL_ONLINE -> onlineDevices.forEach { dev -> onSendFiles(dev.id, uris) }
            explicit != null -> onSendFiles(explicit, uris)
            sendToAll -> onlineDevices.forEach { dev -> onSendFiles(dev.id, uris) }
            target != null -> onSendFiles(target.id, uris)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        val explicit = pendingPickerDeviceId
        pendingPickerDeviceId = null
        when {
            explicit == MESH_TARGET_ALL_ONLINE -> onlineDevices.forEach { dev -> onSendFolder(dev.id, tree) }
            explicit != null -> onSendFolder(explicit, tree)
            sendToAll -> onlineDevices.forEach { dev -> onSendFolder(dev.id, tree) }
            target != null -> onSendFolder(target.id, tree)
        }
    }

    val targetLabel = when {
        sendToAll -> "Mesh (${onlineDevices.size} Online)"
        target != null -> target.name
        else -> stringResource(R.string.not_connected)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 104.dp),
    ) {
        TwinPageHead(
            title = stringResource(R.string.transfers_title),
            subtitle = when {
                sendToAll -> "${onlineDevices.size} ${stringResource(R.string.connected).lowercase()}"
                target != null -> if (target.online) {
                    "${target.name} · ${stringResource(R.string.connected)}"
                } else {
                    "${target.name} · ${stringResource(R.string.not_connected)}"
                }
                else -> stringResource(R.string.not_connected)
            },
        )

        if (allDevices.isEmpty()) {
            TwinCard {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.not_connected), color = pal.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    TwinPillButton(label = stringResource(R.string.action_pair_new), primary = true, onClick = onPairNew)
                }
            }
            return
        }

        // Mesh Ecosystem Device Selector Strip: choose ANY paired PC, Laptop, Phone, or Tablet (or All Online).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allDevices.forEach { dev ->
                val isSelected = !sendToAll && target?.id == dev.id
                Row(
                    modifier = Modifier
                        .shadow(
                            elevation = if (isSelected) 6.dp else 2.dp,
                            shape = RoundedCornerShape(50),
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                        )
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) pal.accent else pal.card)
                        .clickable { chosenTargetId = dev.id }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        imageVector = if (dev.isAndroid) TwinIcons.phone else TwinIcons.screen,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else pal.muted,
                        modifier = Modifier.size(16.dp),
                    )
                    TwinDot(
                        color = if (dev.online) (if (isSelected) Color.White else pal.good) else pal.faint,
                        size = 7.dp,
                    )
                    Text(
                        text = dev.name,
                        color = if (isSelected) Color.White else pal.text,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
            if (onlineDevices.size >= 2) {
                Row(
                    modifier = Modifier
                        .shadow(
                            elevation = if (sendToAll) 6.dp else 2.dp,
                            shape = RoundedCornerShape(50),
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                        )
                        .clip(RoundedCornerShape(50))
                        .background(if (sendToAll) pal.accent else pal.card)
                        .clickable { chosenTargetId = MESH_TARGET_ALL_ONLINE }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        imageVector = TwinIcons.mesh,
                        contentDescription = null,
                        tint = if (sendToAll) Color.White else pal.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Mesh (${onlineDevices.size})",
                        color = if (sendToAll) Color.White else pal.text,
                        fontSize = 13.sp,
                        fontWeight = if (sendToAll) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }

        TwinCard(title = targetLabel) {
            TwinRow(
                label = stringResource(R.string.action_send_files),
                hint = targetLabel,
                first = true,
                icon = TwinIcons.send,
                tileColors = TwinTileGradients.emerald,
                onClick = {
                    pendingPickerDeviceId = if (sendToAll) MESH_TARGET_ALL_ONLINE else target?.id
                    filePicker.launch(arrayOf("*/*"))
                },
            )
            TwinRow(
                label = stringResource(R.string.action_send_folder),
                hint = targetLabel,
                icon = TwinIcons.files,
                tileColors = TwinTileGradients.cyan,
                onClick = {
                    pendingPickerDeviceId = if (sendToAll) MESH_TARGET_ALL_ONLINE else target?.id
                    folderPicker.launch(null)
                },
            )
            TwinRow(
                label = stringResource(R.string.action_send_clipboard),
                hint = targetLabel,
                icon = TwinIcons.clipboard,
                tileColors = TwinTileGradients.amber,
                onClick = {
                    val destId = if (sendToAll) null else target?.id
                    context.startActivity(SendActivity.sendClipboardIntent(context, destId))
                },
            )
        }

        // All Paired Mesh Ecosystem Devices: tap any device to send files directly to it
        TwinCard(
            title = "LinkHub Mesh (${allDevices.size})",
            action = {
                Text(
                    text = "+ " + stringResource(R.string.action_pair_new),
                    color = pal.accent,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onPairNew)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            },
        ) {
            allDevices.forEachIndexed { idx, dev ->
                val linkDesc = when (val link = dev.link) {
                    is Link.Online -> when {
                        link.usb -> stringResource(R.string.link_usb)
                        link.relayed -> stringResource(R.string.link_away)
                        else -> "Wi-Fi"
                    }
                    Link.Connecting -> stringResource(R.string.link_connecting)
                    is Link.Offline -> stringResource(R.string.not_connected)
                }
                TwinRow(
                    label = dev.name,
                    hint = (if (dev.isAndroid) "Android" else "PC") + " · " + linkDesc,
                    first = idx == 0,
                    icon = if (dev.isAndroid) TwinIcons.phone else TwinIcons.screen,
                    tileColors = if (dev.isAndroid) TwinTileGradients.purple else TwinTileGradients.blue,
                    activeDot = dev.online,
                    onClick = {
                        chosenTargetId = dev.id
                        pendingPickerDeviceId = dev.id
                        filePicker.launch(arrayOf("*/*"))
                    },
                    trailing = {
                        TwinPillButton(
                            label = stringResource(R.string.action_send_files),
                            primary = !sendToAll && target?.id == dev.id,
                            onClick = {
                                chosenTargetId = dev.id
                                pendingPickerDeviceId = dev.id
                                filePicker.launch(arrayOf("*/*"))
                            },
                        )
                    },
                )
            }
        }

        if (target != null && !target.isAndroid) {
            TwinCard(title = stringResource(R.string.action_remote_pc)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TwinPillButton(
                        label = stringResource(R.string.action_ring_pc),
                        onClick = { onRing(target.id, true) },
                        modifier = Modifier.weight(1f),
                    )
                    TwinPillButton(
                        label = stringResource(R.string.action_lock_pc),
                        onClick = { onPower(target.id, false) },
                        modifier = Modifier.weight(1f),
                    )
                    TwinPillButton(
                        label = stringResource(R.string.action_sleep_pc),
                        onClick = { onPower(target.id, true) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else if (target != null && target.isAndroid) {
            TwinCard(title = target.name) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TwinPillButton(
                        label = stringResource(R.string.action_ring_device),
                        onClick = { onRing(target.id, true) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * TwinDesk-style Mesh Ecosystem Hub tab: shows this phone + all paired PCs/phones/tablets
 * in 1 unified ecosystem, their live connection path (Wi-Fi / USB / Away Relay), RTT,
 * and quick 1-tap actions (Send files, Ring, Open Screen).
 */
@Composable
fun TwinMeshTab(
    state: CoreState,
    onSendFiles: (deviceId: String, uris: List<Uri>) -> Unit,
    onOpenRemote: (pcId: String, mode: RemoteMode) -> Unit,
    onRing: (id: String, on: Boolean) -> Unit,
    onRefresh: () -> Unit,
    onPairNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalTwinPalette.current
    val context = LocalContext.current
    val allDevices = state.devices
    val onlineCount = allDevices.count { it.online }
    var pendingPickerId by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val id = pendingPickerId
        pendingPickerId = null
        if (uris.isNotEmpty() && id != null) {
            if (id == MESH_TARGET_ALL_ONLINE) {
                val targets = allDevices.filter { it.online }.ifEmpty { allDevices }
                targets.forEach { dev -> onSendFiles(dev.id, uris) }
            } else {
                onSendFiles(id, uris)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 104.dp),
    ) {
        TwinPageHead(
            title = "LinkHub Mesh",
            subtitle = if (allDevices.isEmpty()) {
                stringResource(R.string.not_connected)
            } else {
                "$onlineCount / ${allDevices.size} ${stringResource(R.string.connected).lowercase()}"
            },
        )

        // 1. Unified Ecosystem Hub Status Card
        TwinCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TwinTile(TwinIcons.mesh, TwinTileGradients.blue, size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = android.os.Build.MODEL ?: stringResource(R.string.app_name),
                                color = pal.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (onlineCount > 0) pal.good.copy(alpha = 0.16f) else pal.chip)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = if (onlineCount > 0) "1 Ekosistem" else stringResource(R.string.not_connected),
                                    color = if (onlineCount > 0) pal.good else pal.muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Text(
                            text = "v${app.nectarlink.android.BuildConfig.VERSION_NAME} · " + (if (state.awayMode) "Wi-Fi · USB · Away Relay" else "Wi-Fi / LAN · USB"),
                            color = pal.muted,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TwinPillButton(
                        label = stringResource(R.string.action_refresh),
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                    )
                    TwinPillButton(
                        label = stringResource(R.string.action_pair_new),
                        primary = true,
                        onClick = onPairNew,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (allDevices.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TwinPillButton(
                            label = "Broadcast File (${allDevices.size})",
                            onClick = {
                                pendingPickerId = MESH_TARGET_ALL_ONLINE
                                filePicker.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TwinPillButton(
                            label = "Broadcast Klip",
                            onClick = {
                                context.startActivity(SendActivity.sendClipboardIntent(context, null))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (allDevices.isEmpty()) {
            TwinCard(title = stringResource(R.string.app_name)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.pair_scan_hint),
                        color = pal.muted,
                        fontSize = 13.5.sp,
                    )
                }
            }
            return
        }

        // 2. All Ecosystem Nodes
        TwinCard(title = "Perangkat dalam Ekosistem (${allDevices.size})") {
            allDevices.forEachIndexed { idx, dev ->
                val linkDesc = when (val link = dev.link) {
                    is Link.Online -> {
                        val via = when {
                            link.usb -> stringResource(R.string.link_usb)
                            link.relayed -> stringResource(R.string.link_away)
                            else -> "Wi-Fi"
                        }
                        val rtt = link.rttMs.takeIf { it > 0u }?.let { " · ${it} ms" }.orEmpty()
                        "$via$rtt"
                    }
                    Link.Connecting -> stringResource(R.string.link_connecting)
                    is Link.Offline -> stringResource(R.string.not_connected)
                }
                val verBadge = dev.model
                    ?.substringAfter("· ", "")
                    ?.takeIf { it.startsWith("v") }
                    ?: dev.model?.takeIf { it.contains("v0.") || it.contains("v1.") }
                val baseType = if (dev.isAndroid) "Android" else "PC"
                val hintParts = listOfNotNull(
                    baseType,
                    verBadge,
                    linkDesc,
                ).joinToString(" · ")
                Column {
                    TwinRow(
                        label = dev.name,
                        hint = hintParts,
                        first = idx == 0,
                        icon = if (dev.isAndroid) TwinIcons.phone else TwinIcons.screen,
                        tileColors = if (dev.isAndroid) TwinTileGradients.purple else TwinTileGradients.blue,
                        activeDot = dev.online,
                        trailing = {
                            if (!dev.isAndroid && dev.online) {
                                TwinPillButton(
                                    label = stringResource(R.string.remote_mode_screen),
                                    onClick = { onOpenRemote(dev.id, RemoteMode.Screen) },
                                )
                            } else {
                                TwinPillButton(
                                    label = stringResource(R.string.action_send_files),
                                    primary = dev.online,
                                    onClick = {
                                        pendingPickerId = dev.id
                                        filePicker.launch(arrayOf("*/*"))
                                    },
                                )
                            }
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 62.dp, end = 18.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TwinPillButton(
                            label = stringResource(R.string.action_send_files),
                            onClick = {
                                pendingPickerId = dev.id
                                filePicker.launch(arrayOf("*/*"))
                            },
                        )
                        TwinPillButton(
                            label = stringResource(R.string.action_send_clipboard),
                            onClick = { context.startActivity(SendActivity.sendClipboardIntent(context, dev.id)) },
                        )
                        TwinPillButton(
                            label = stringResource(R.string.action_ring_device),
                            enabled = dev.online,
                            onClick = { onRing(dev.id, true) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * TwinDesk's iconic "More" page: grouped cards of colored gradient icon tiles leading to
 * every LinkHub feature (Deck, Webcam, Voice Recorder, Desk, Pairing, Guide, and Settings).
 */
@Composable
fun TwinMoreTab(
    state: CoreState,
    webcamActive: Boolean,
    recorderActive: Boolean,
    onOpenDeck: (pcId: String) -> Unit,
    onOpenDesk: (pcId: String) -> Unit,
    onOpenWebcam: (pcId: String) -> Unit,
    onOpenRecorder: (pcId: String) -> Unit,
    onPairNew: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeaturesGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pc = state.devices.firstOrNull { !it.isAndroid && it.online }
        ?: state.devices.firstOrNull { !it.isAndroid }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 104.dp),
    ) {
        TwinPageHead(title = stringResource(R.string.settings_language_more))

        if (pc != null) {
            TwinCard(title = pc.name) {
                TwinRow(
                    label = stringResource(R.string.action_deck_pc),
                    hint = stringResource(R.string.deck_hint, pc.name),
                    first = true,
                    icon = TwinIcons.deck,
                    tileColors = TwinTileGradients.purple,
                    onClick = { onOpenDeck(pc.id) },
                )
                TwinRow(
                    label = stringResource(R.string.action_webcam_pc),
                    hint = stringResource(R.string.webcam_status_ready, pc.name),
                    icon = TwinIcons.camera,
                    tileColors = TwinTileGradients.rose,
                    activeDot = webcamActive,
                    onClick = { onOpenWebcam(pc.id) },
                )
                TwinRow(
                    label = stringResource(R.string.action_record_pc),
                    hint = stringResource(R.string.recorder_status_ready, pc.name),
                    icon = TwinIcons.mic,
                    tileColors = TwinTileGradients.cyan,
                    activeDot = recorderActive,
                    onClick = { onOpenRecorder(pc.id) },
                )
                TwinRow(
                    label = stringResource(R.string.action_desk_pc),
                    hint = pc.name,
                    icon = TwinIcons.clock,
                    tileColors = TwinTileGradients.indigo,
                    onClick = { onOpenDesk(pc.id) },
                )
            }
        }

        TwinCard(title = stringResource(R.string.app_name)) {
            TwinRow(
                label = stringResource(R.string.action_pair_new),
                hint = stringResource(R.string.pair_title),
                first = true,
                icon = TwinIcons.qr,
                tileColors = TwinTileGradients.blue,
                onClick = onPairNew,
            )
            TwinRow(
                label = stringResource(R.string.settings_title),
                hint = stringResource(R.string.settings_appearance),
                icon = TwinIcons.settings,
                tileColors = TwinTileGradients.slate,
                onClick = onOpenSettings,
            )
            TwinRow(
                label = stringResource(R.string.home_guide_title),
                hint = stringResource(R.string.home_whats_new_guide),
                icon = TwinIcons.guide,
                tileColors = TwinTileGradients.emerald,
                onClick = onOpenFeaturesGuide,
            )
            TwinRow(
                label = "Versi Aplikasi LinkHub",
                hint = "v${app.nectarlink.android.BuildConfig.VERSION_NAME} (Kode ${app.nectarlink.android.BuildConfig.VERSION_CODE})",
                icon = TwinIcons.mesh,
                tileColors = TwinTileGradients.indigo,
            )
        }
    }
}
