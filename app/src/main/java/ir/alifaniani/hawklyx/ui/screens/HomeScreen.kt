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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.alifaniani.hawklyx.R
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.data.model.DataFreshnessStatus
import ir.alifaniani.hawklyx.data.model.PriceUiState
import ir.alifaniani.hawklyx.data.model.HistoryRange
import ir.alifaniani.hawklyx.data.model.HistoryUiState
import ir.alifaniani.hawklyx.data.schedule.UtcRefreshSchedule
import ir.alifaniani.hawklyx.domain.HawklyxPriceColorResolver
import ir.alifaniani.hawklyx.domain.PriceChange24h
import ir.alifaniani.hawklyx.domain.PriceChangeDirection
import ir.alifaniani.hawklyx.ui.components.HawklyxCircleLoader
import ir.alifaniani.hawklyx.ui.components.HawklyxCooldownProgress
import ir.alifaniani.hawklyx.ui.components.HawklyxMeridianLoader
import ir.alifaniani.hawklyx.ui.components.MarketHistoryChart
import ir.alifaniani.hawklyx.ui.theme.HawklyxAmber
import ir.alifaniani.hawklyx.ui.theme.HawklyxBorder
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonClosedBorder
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonClosedContainer
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonClosedContent
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonDisabledBorder
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonDisabledContainer
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonEmeraldBorder
import ir.alifaniani.hawklyx.ui.theme.HawklyxButtonEmeraldContainer
import ir.alifaniani.hawklyx.ui.theme.HawklyxDarkBackground
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald
import ir.alifaniani.hawklyx.ui.theme.HawklyxRed
import ir.alifaniani.hawklyx.ui.theme.HawklyxSurface
import ir.alifaniani.hawklyx.ui.theme.HawklyxSurfaceVariant
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextMuted
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextPrimary
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextSecondary
import ir.alifaniani.hawklyx.util.HawklyxTimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: PriceUiState,
    historyUiState: HistoryUiState = HistoryUiState(),
    onRefresh: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onSelectRange: (HistoryRange) -> Unit = {},
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

            // 4. Scheduled Cadence Card
            ScheduledCadenceCard(uiState = uiState)

            // 5. Market History Chart (Area/Line with genuine observations)
            MarketHistoryChart(
                historyUiState = historyUiState,
                currentObservation = uiState.observation,
                status = uiState.status,
                isMarketClosed = uiState.isMarketClosed,
                sessionDirection = uiState.sessionPriceState.direction,
                isBaselineAvailable = uiState.sessionPriceState.isAvailable,
                onSelectRange = onSelectRange
            )

            // 6. Market Provenance & Architecture Specifications
            MarketProvenanceCard(observation = uiState.observation)
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
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.hawklyx_logo),
                contentDescription = "HAWKLYX Logo",
                modifier = Modifier.size(60.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(14.dp))
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

        Spacer(modifier = Modifier.width(8.dp))

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
    val isClosed = uiState.isMarketClosed
    val isRefreshing = uiState.isRefreshing
    val cooldownSec = uiState.cooldownRemainingSeconds
    val isCooldownActive = cooldownSec > 0

    val priceColor = HawklyxPriceColorResolver.resolveComposeColor(
        observation = observation,
        status = status,
        isMarketClosed = isClosed,
        sessionDirection = uiState.sessionPriceState.direction
    )

    val infiniteTransition = rememberInfiniteTransition(label = "badge_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        // LAYER A — Compact Instrument Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BRENT CRUDE SPOT",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = HawklyxTextSecondary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(10.dp))

            StatusBadge(
                status = status,
                isRefreshing = isRefreshing,
                pulseAlpha = pulseAlpha
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // LAYER B — Dominant Price Display
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWide = maxWidth >= 420.dp
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = observation?.formatted ?: "$--.--",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                                color = priceColor
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.alignByBaseline()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = observation?.unit ?: "USD / barrel",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = HawklyxTextSecondary
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .alignByBaseline()
                                .padding(bottom = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 24-Hour Price Change Indicator Pill
                    PriceChange24hBadge(
                        priceChange = uiState.priceChange24h,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = observation?.formatted ?: "$--.--",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                                color = priceColor
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.alignByBaseline()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = observation?.unit ?: "USD / barrel",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = HawklyxTextSecondary
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .alignByBaseline()
                                .padding(bottom = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 24-Hour Price Change Indicator Pill
                    PriceChange24hBadge(
                        priceChange = uiState.priceChange24h
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = HawklyxBorder.copy(alpha = 0.6f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))

        // LAYER C — Minimal Market Metadata
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            MetadataRow(
                label = "Source observation",
                timestamp = formatIsoTimestamp(observation?.asOf)
            )
            HorizontalDivider(color = HawklyxBorder.copy(alpha = 0.35f), thickness = 0.5.dp)
            MetadataRow(
                label = "Last edge sync",
                timestamp = formatIsoTimestamp(observation?.fetchedAt)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // LAYER D — Refresh Action and Feedback
        val isButtonActive = !isClosed && !isRefreshing && !isCooldownActive
        val buttonContainerColor = when {
            isClosed -> HawklyxButtonClosedContainer
            isButtonActive -> HawklyxButtonEmeraldContainer
            else -> HawklyxButtonDisabledContainer
        }
        val buttonBorderColor = when {
            isClosed -> HawklyxButtonClosedBorder
            isButtonActive -> HawklyxButtonEmeraldBorder
            else -> HawklyxButtonDisabledBorder
        }
        val buttonContentColor = when {
            isClosed -> HawklyxButtonClosedContent
            isButtonActive -> HawklyxEmerald
            else -> HawklyxTextMuted
        }

        ElevatedButton(
            onClick = onRefresh,
            enabled = isButtonActive,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(
                    width = 1.dp,
                    color = buttonBorderColor,
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = buttonContainerColor,
                contentColor = buttonContentColor,
                disabledContainerColor = buttonContainerColor,
                disabledContentColor = buttonContentColor
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 2.dp,
                disabledElevation = 0.dp
            )
        ) {
            when {
                isClosed -> {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Market Closed",
                        tint = buttonContentColor,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Market Closed • Refresh Paused",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                            color = buttonContentColor
                        )
                    )
                }
                isRefreshing -> {
                    HawklyxCircleLoader(size = 18.dp, strokeWidth = 2.dp)
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
                        size = 16.dp,
                        color = HawklyxTextMuted
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Next refresh in ${UtcRefreshSchedule.formatRemaining(cooldownSec)}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = HawklyxTextMuted
                        )
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Price",
                        tint = HawklyxEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Refresh Price Now",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = HawklyxEmerald
                        )
                    )
                }
            }
        }

        AnimatedVisibility(visible = !isClosed && uiState.message.isNotBlank()) {
            Text(
                text = uiState.message,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    color = HawklyxTextMuted,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    timestamp: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 260.dp
        if (isNarrow) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        color = HawklyxTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HawklyxTextPrimary
                    )
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = HawklyxTextSecondary,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HawklyxTextPrimary
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ScheduledCadenceCard(uiState: PriceUiState) {
    val isClosed = uiState.isMarketClosed
    val cadenceFormatted = if (isClosed) "PAUSED" else UtcRefreshSchedule.formatRemaining(uiState.cadenceRemainingSeconds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Compact Responsive Header without truncation
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isNarrowHeader = maxWidth < 315.dp

            if (isNarrowHeader) {
                // Stacked responsive arrangement for narrow screens / larger font scales
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(HawklyxSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (isClosed) HawklyxAmber else HawklyxEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SCHEDULED EDGE CADENCE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                                color = HawklyxTextSecondary
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CadenceModeBadge(isClosed = isClosed)
                    }
                }
            } else {
                // Single-line arrangement when sufficient horizontal width exists
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(HawklyxSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (isClosed) HawklyxAmber else HawklyxEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SCHEDULED EDGE CADENCE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Bold,
                                color = HawklyxTextSecondary
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    CadenceModeBadge(isClosed = isClosed)
                }
            }
        }

        // 2. Primary Status Area: Precision Sync Instrument
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HawklyxDarkBackground.copy(alpha = 0.6f))
                .border(0.75.dp, HawklyxBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = if (isClosed) "Edge Cadence Paused" else "Next Edge Sync In",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = HawklyxTextSecondary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cadenceFormatted,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (isClosed) 20.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (isClosed) 0.5.sp else 1.5.sp,
                        color = if (isClosed) HawklyxAmber else HawklyxEmerald
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // The Dominik Kissi Meridian Sphere Loader Instrument (authentically animated)
            HawklyxMeridianLoader(
                size = 38.dp,
                strokeWidth = 1.2.dp,
                accentColor = if (isClosed) HawklyxAmber else HawklyxEmerald,
                wireframeColor = (if (isClosed) HawklyxAmber else HawklyxEmerald).copy(alpha = 0.28f),
                rimBaseColor = (if (isClosed) HawklyxAmber else HawklyxEmerald).copy(alpha = 0.40f)
            )
        }

        // 3. Concise Supporting Explanation
        Text(
            text = if (isClosed) {
                "Background updates are paused during market closure to protect daily API budget. Cadence resumes at Sunday 23:00 London weekly opening."
            } else {
                "Background updates follow a 15-minute cadence. Android may defer execution because of battery optimization and Doze mode."
            },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.5.sp,
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
        status == DataFreshnessStatus.MARKET_CLOSED -> "MARKET CLOSED" to HawklyxAmber
        status == DataFreshnessStatus.STALE -> "STALE" to HawklyxAmber
        status == DataFreshnessStatus.QUOTA_EXHAUSTED -> "QUOTA LIMIT" to HawklyxRed
        status == DataFreshnessStatus.PROVIDER_UNAVAILABLE -> "OFFLINE" to HawklyxAmber
        else -> "NO DATA" to HawklyxTextMuted
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(dotColor.copy(alpha = 0.10f))
            .border(0.75.dp, dotColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(if (status == DataFreshnessStatus.FRESH || isRefreshing) pulseAlpha else 1f)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = dotColor
            )
        )
    }
}

@Composable
private fun CadenceModeBadge(
    isClosed: Boolean,
    modifier: Modifier = Modifier
) {
    val (label, accentColor) = if (isClosed) {
        "MARKET CLOSED" to HawklyxAmber
    } else {
        "15-MIN CADENCE" to HawklyxEmerald
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.10f))
            .border(0.75.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = accentColor
            ),
            maxLines = 1,
            softWrap = false
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
            .padding(20.dp)
    ) {
        Text(
            text = "MARKET SPECIFICATIONS",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = HawklyxTextSecondary
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        ProvenanceRow(label = "Commodity Benchmark", value = observation?.name ?: "Brent Crude Oil")
        ProvenanceRow(label = "Contract Symbol", value = observation?.code ?: "BRENT_CRUDE_USD")
        ProvenanceRow(label = "Denomination", value = "USD per barrel (bbl)")
        ProvenanceRow(label = "Edge Upstream Source", value = observation?.source ?: "OilPriceAPI")
        ProvenanceRow(label = "Edge Coordination", value = "Cloudflare Durable Object")
        ProvenanceRow(label = "Refresh Schedule", value = "15-min cadence (3-min cooldown)", showDivider = false)
    }
}

@Composable
private fun PriceChange24hBadge(
    priceChange: PriceChange24h,
    modifier: Modifier = Modifier
) {
    val (textColor, containerColor, borderColor, icon) = when {
        !priceChange.isAvailable -> Quad(
            HawklyxTextMuted,
            HawklyxSurfaceVariant,
            HawklyxBorder,
            null
        )
        priceChange.direction == PriceChangeDirection.POSITIVE -> Quad(
            HawklyxEmerald,
            HawklyxButtonEmeraldContainer,
            HawklyxButtonEmeraldBorder,
            Icons.Default.ArrowUpward
        )
        priceChange.direction == PriceChangeDirection.NEGATIVE -> Quad(
            HawklyxRed,
            HawklyxRed.copy(alpha = 0.12f),
            HawklyxRed.copy(alpha = 0.30f),
            Icons.Default.ArrowDownward
        )
        else -> Quad(
            HawklyxTextSecondary,
            HawklyxSurfaceVariant,
            HawklyxBorder,
            Icons.Default.ArrowForward
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            text = if (priceChange.isAvailable) "24h ${priceChange.formattedPercentage}" else "24h —",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = textColor,
                letterSpacing = 0.2.sp
            )
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun ProvenanceRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = HawklyxTextMuted,
                    fontWeight = FontWeight.Normal
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HawklyxTextPrimary
                )
            )
        }
        if (showDivider) {
            HorizontalDivider(color = HawklyxBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
        }
    }
}

private fun formatIsoTimestamp(isoString: String?): String {
    return HawklyxTimeUtils.formatIsoToLocalTime(isoString)
}
