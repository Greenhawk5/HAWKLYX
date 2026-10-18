package ir.alifaniani.hawklyx.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.alifaniani.hawklyx.R
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.PriceUiState
import ir.alifaniani.hawklyx.data.schedule.UtcRefreshSchedule
import ir.alifaniani.hawklyx.ui.components.HawklyxCircleLoader
import ir.alifaniani.hawklyx.ui.components.HawklyxCooldownProgress
import ir.alifaniani.hawklyx.ui.components.MarketHistoryChart
import ir.alifaniani.hawklyx.ui.theme.HawklyxAmber
import ir.alifaniani.hawklyx.ui.theme.HawklyxBorder
import ir.alifaniani.hawklyx.ui.theme.HawklyxDarkBackground
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald
import ir.alifaniani.hawklyx.ui.theme.HawklyxRed
import ir.alifaniani.hawklyx.ui.theme.HawklyxSurface
import ir.alifaniani.hawklyx.ui.theme.HawklyxSurfaceVariant
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextMuted
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextPrimary
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: PriceUiState,
    onRefresh: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HawklyxDarkBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Top Branding & About action
        TopBrandingHeader(onNavigateToAbout = onNavigateToAbout)

        // 2. Initial Loading state when awaiting first sync without cached data
        if (uiState.observation == null && uiState.isRefreshing) {
            InitialLoadingCard()
        } else {
            // 3. Dominant Hero Brent Crude Price Card
            HeroPriceCard(
                uiState = uiState,
                onRefresh = onRefresh
            )

            // 4. UTC Quarter-Hour Scheduled Cadence Card
            ScheduledCadenceCard(uiState = uiState)

            // 5. Market History Chart (Area/Line with genuine observations)
            MarketHistoryChart(
                history = uiState.history,
                currentObservation = uiState.observation
            )

            // 6. Market Provenance & Architecture Specifications
            MarketProvenanceCard(observation = uiState.observation)

            // 7. Home-Screen Widget Guide
            WidgetGuideCard()
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TopBrandingHeader(onNavigateToAbout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HawklyxSurfaceVariant)
                    .border(1.dp, HawklyxBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hawklyx_icon_small),
                    contentDescription = "HAWKLYX Logo",
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "HAWKLYX",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = HawklyxTextPrimary
                    )
                )
                Text(
                    text = "Brent Crude Price Monitor",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.5.sp,
                        color = HawklyxTextSecondary
                    )
                )
            }
        }

        IconButton(
            onClick = onNavigateToAbout,
            modifier = Modifier
                .clip(CircleShape)
                .background(HawklyxSurfaceVariant)
                .border(1.dp, HawklyxBorder, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "About HAWKLYX and Architecture",
                tint = HawklyxTextSecondary
            )
        }
    }
}

@Composable
private fun InitialLoadingCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(20.dp))
            .padding(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HawklyxCircleLoader(size = 54.dp, strokeWidth = 3.5.dp)
            Text(
                text = "Connecting to HAWKLYX Edge Service...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = HawklyxTextPrimary
                )
            )
            Text(
                text = "Synchronizing with Cloudflare edge coordinator",
                style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted)
            )
        }
    }
}

@Composable
private fun HeroPriceCard(
    uiState: PriceUiState,
    onRefresh: () -> Unit
) {
    val observation = uiState.observation
    val status = uiState.status
    val isRefreshing = uiState.isRefreshing
    val cooldownSec = uiState.cooldownRemainingSeconds
    val isCooldownActive = cooldownSec > 0

    val infiniteTransition = rememberInfiniteTransition(label = "badge_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(20.dp))
            .padding(22.dp)
    ) {
        Column {
            // Card Header: Instrument title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BRENT CRUDE SPOT",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = HawklyxTextSecondary
                    )
                )

                StatusBadge(status = status, isRefreshing = isRefreshing, pulseAlpha = pulseAlpha)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dominant Price Display
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = observation?.formatted ?: "$--.--",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (observation != null) HawklyxEmerald else HawklyxTextSecondary
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = observation?.unit ?: "USD / barrel",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = HawklyxTextSecondary
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = HawklyxBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Timestamps Breakdown: Redesigned as distinct information tiles to eliminate overlap
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TimestampTile(
                    icon = Icons.Default.AccessTime,
                    label = "Source Market Observation",
                    timestamp = formatIsoTimestamp(observation?.asOf),
                    sublabel = "Original provider benchmark timestamp"
                )
                TimestampTile(
                    icon = Icons.Default.CloudDone,
                    label = "Last Edge Coordination",
                    timestamp = formatIsoTimestamp(observation?.fetchedAt),
                    sublabel = "Cloudflare edge validation time"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Refresh Control Button
            ElevatedButton(
                onClick = onRefresh,
                enabled = !isRefreshing && !isCooldownActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = HawklyxEmerald,
                    contentColor = HawklyxDarkBackground,
                    disabledContainerColor = HawklyxSurfaceVariant,
                    disabledContentColor = HawklyxTextMuted
                )
            ) {
                when {
                    isRefreshing -> {
                        HawklyxCircleLoader(size = 20.dp, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Syncing with edge...",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = HawklyxTextSecondary
                            )
                        )
                    }
                    isCooldownActive -> {
                        val progress = (cooldownSec.toFloat() / 180f).coerceIn(0f, 1f)
                        HawklyxCooldownProgress(
                            progress = progress,
                            size = 18.dp,
                            color = HawklyxTextMuted
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Next manual refresh in ${UtcRefreshSchedule.formatRemaining(cooldownSec)}",
                            style = MaterialTheme.typography.labelLarge.copy(color = HawklyxTextMuted)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Price",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Refresh Price Now",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = uiState.message.isNotBlank()) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        color = HawklyxTextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun TimestampTile(
    icon: ImageVector,
    label: String,
    timestamp: String,
    sublabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HawklyxSurfaceVariant)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HawklyxSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HawklyxEmerald,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = HawklyxTextSecondary
                )
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = HawklyxTextMuted
                )
            )
        }

        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = HawklyxTextPrimary
            )
        )
    }
}

@Composable
private fun ScheduledCadenceCard(uiState: PriceUiState) {
    val cadenceFormatted = UtcRefreshSchedule.formatRemaining(uiState.cadenceRemainingSeconds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(HawklyxSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = HawklyxEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "SCHEDULED EDGE CADENCE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold,
                            color = HawklyxTextSecondary
                        )
                    )
                    Text(
                        text = "UTC quarter-hour schedule (:00, :15, :30, :45)",
                        style = MaterialTheme.typography.labelSmall.copy(color = HawklyxTextMuted)
                    )
                }
            }

            Text(
                text = cadenceFormatted,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = HawklyxEmerald
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Background synchronization uses Android WorkManager aligned to the edge cadence. OS battery preservation and Doze mode may batch requests within the 15-minute window.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = HawklyxTextMuted
            )
        )
    }
}

@Composable
private fun StatusBadge(
    status: DataFreshnessStatus,
    isRefreshing: Boolean,
    pulseAlpha: Float
) {
    val (label, dotColor) = when {
        isRefreshing -> "SYNCING" to HawklyxEmerald
        status == DataFreshnessStatus.FRESH -> "LIVE" to HawklyxEmerald
        status == DataFreshnessStatus.STALE -> "STALE" to HawklyxAmber
        status == DataFreshnessStatus.QUOTA_EXHAUSTED -> "QUOTA LIMIT" to HawklyxRed
        status == DataFreshnessStatus.PROVIDER_UNAVAILABLE -> "OFFLINE" to HawklyxAmber
        else -> "NO DATA" to HawklyxTextMuted
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HawklyxSurfaceVariant)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(if (status == DataFreshnessStatus.FRESH || isRefreshing) pulseAlpha else 1f)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = dotColor
            )
        )
    }
}

@Composable
private fun MarketProvenanceCard(observation: BrentPriceObservation?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "MARKET SPECIFICATIONS",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = HawklyxTextSecondary
            )
        )

        ProvenanceRow(label = "Commodity Benchmark", value = observation?.name ?: "Brent Crude Oil")
        ProvenanceRow(label = "Contract Symbol", value = observation?.code ?: "BRENT_CRUDE_USD")
        ProvenanceRow(label = "Denomination", value = "USD per barrel (bbl)")
        ProvenanceRow(label = "Edge Upstream Source", value = observation?.source ?: "OilPriceAPI")
        ProvenanceRow(label = "Edge Coordination", value = "Cloudflare Durable Object")
        ProvenanceRow(label = "Refresh Schedule", value = "15-min UTC cadence (3-min cooldown)")
    }
}

@Composable
private fun ProvenanceRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = HawklyxTextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HawklyxTextPrimary
            )
        )
    }
}

@Composable
private fun WidgetGuideCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HawklyxSurfaceVariant)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HawklyxSurface)
                .border(1.dp, HawklyxBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                tint = HawklyxEmerald,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Home-Screen Widget Available",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = HawklyxTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Long-press your home screen to add the 3x2 HAWKLYX widget for instant glanceable Brent crude monitoring.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = HawklyxTextSecondary
            )
        }
    }
}

private fun formatIsoTimestamp(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "--:--"
    return try {
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss (z)", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: Exception) {
        isoString.take(19).replace("T", " ")
    }
}
