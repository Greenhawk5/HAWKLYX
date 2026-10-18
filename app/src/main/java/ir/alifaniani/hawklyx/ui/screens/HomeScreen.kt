package ir.alifaniani.hawklyx.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.PriceUiState
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
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top App Bar / Branding
        TopBrandingHeader(onNavigateToAbout = onNavigateToAbout)

        // Hero Brent Crude Price Card
        HeroPriceCard(
            observation = uiState.observation,
            status = uiState.status,
            isRefreshing = uiState.isRefreshing,
            cooldownRemainingSeconds = uiState.cooldownRemainingSeconds,
            message = uiState.message,
            onRefresh = onRefresh
        )

        // Market Specifications & Attribution Card
        MarketSpecificationsCard(observation = uiState.observation)

        // Home Widget Setup Guide Card
        WidgetGuideCard()

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
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(HawklyxEmerald)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HAWKLYX",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = HawklyxTextPrimary
                    )
                )
            }
            Text(
                text = "Brent Crude Price Monitor",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.5.sp,
                    color = HawklyxTextSecondary
                ),
                modifier = Modifier.padding(start = 18.dp)
            )
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
                contentDescription = "About HAWKLYX",
                tint = HawklyxTextSecondary
            )
        }
    }
}

@Composable
private fun HeroPriceCard(
    observation: BrentPriceObservation?,
    status: DataFreshnessStatus,
    isRefreshing: Boolean,
    cooldownRemainingSeconds: Int,
    message: String,
    onRefresh: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
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
            .padding(24.dp)
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

                // Freshness Badge
                StatusBadge(status = status, isRefreshing = isRefreshing, pulseAlpha = pulseAlpha)
            }

            Spacer(modifier = Modifier.height(18.dp))

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

            // Timestamps Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Source Observation",
                        style = MaterialTheme.typography.labelSmall,
                        color = HawklyxTextMuted
                    )
                    Text(
                        text = formatIsoTimestamp(observation?.asOf),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = HawklyxTextPrimary
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Last Edge Sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = HawklyxTextMuted
                    )
                    Text(
                        text = formatIsoTimestamp(observation?.fetchedAt),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = HawklyxTextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Refresh Control Button
            val isCooldownActive = cooldownRemainingSeconds > 0
            ElevatedButton(
                onClick = onRefresh,
                enabled = !isRefreshing && !isCooldownActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = HawklyxEmerald,
                    contentColor = HawklyxDarkBackground,
                    disabledContainerColor = HawklyxSurfaceVariant,
                    disabledContentColor = HawklyxTextMuted
                )
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = HawklyxEmerald
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Syncing with edge...",
                        style = MaterialTheme.typography.labelLarge.copy(color = HawklyxTextSecondary)
                    )
                } else if (isCooldownActive) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = HawklyxTextMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Refresh Cooldown (${cooldownRemainingSeconds}s)",
                        style = MaterialTheme.typography.labelLarge.copy(color = HawklyxTextMuted)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Refresh Price Now",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            AnimatedVisibility(visible = message.isNotBlank()) {
                Text(
                    text = message,
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
private fun StatusBadge(
    status: DataFreshnessStatus,
    isRefreshing: Boolean,
    pulseAlpha: Float
) {
    val (label, dotColor) = when {
        isRefreshing -> "SYNCING" to HawklyxTextSecondary
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
                .alpha(if (status == DataFreshnessStatus.FRESH) pulseAlpha else 1f)
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
private fun MarketSpecificationsCard(observation: BrentPriceObservation?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "MARKET PROVENANCE",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = HawklyxTextSecondary
            )
        )

        SpecRow(label = "Commodity Code", value = observation?.code ?: "BRENT_CRUDE_USD")
        SpecRow(label = "Instrument Name", value = observation?.name ?: "Brent Crude Oil")
        SpecRow(label = "Currency & Unit", value = "USD per barrel (bbl)")
        SpecRow(label = "Edge Upstream Source", value = observation?.source ?: "OilPriceAPI")
        SpecRow(label = "Edge Refresh Schedule", value = "15-minute cadence (3-min manual cooldown)")
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = HawklyxTextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
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
                text = "Long-press your home screen and add the HAWKLYX widget for instant glanceable updates.",
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
