package ir.alifaniani.hawklyx.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.alifaniani.hawklyx.R
import ir.alifaniani.hawklyx.data.model.BrentPriceObservation
import ir.alifaniani.hawklyx.ui.theme.HawklyxBorder
import ir.alifaniani.hawklyx.ui.theme.HawklyxDarkBackground
import ir.alifaniani.hawklyx.ui.theme.HawklyxEmerald
import ir.alifaniani.hawklyx.ui.theme.HawklyxSurface
import ir.alifaniani.hawklyx.ui.theme.HawklyxSurfaceVariant
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextMuted
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextPrimary
import ir.alifaniani.hawklyx.ui.theme.HawklyxTextSecondary

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    observation: BrentPriceObservation? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HawklyxDarkBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(HawklyxSurfaceVariant)
                    .border(1.dp, HawklyxBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Return to Home",
                    tint = HawklyxTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "About HAWKLYX",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = HawklyxTextPrimary
            )
        }

        // Hero Brand Showcase Card
        HeroBrandCard()

        // Section: Product Purpose
        AboutSectionCard(
            title = "PURPOSE & PHILOSOPHY",
            icon = Icons.Default.Shield
        ) {
            Text(
                text = "HAWKLYX is a dedicated financial monitor engineered for rapid, glanceable awareness of global Brent crude oil prices.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    color = HawklyxTextSecondary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Built on the philosophy of Minimal Precision, the experience prioritizes the home-screen widget as the primary interface, eliminating dashboard bloat, ads, tracking, and speculative clutter.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    color = HawklyxTextSecondary
                )
            )
        }

        // Section: Home-Screen Widget (Relocated and refined notice)
        AboutSectionCard(
            title = "HOME-SCREEN WIDGET",
            icon = Icons.Default.Widgets
        ) {
            Text(
                text = "HAWKLYX provides an ultra-low-overhead 3×2 Android home-screen widget for instant, glanceable Brent crude monitoring directly from your launcher.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    color = HawklyxTextSecondary
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HawklyxSurfaceVariant)
                    .border(1.dp, HawklyxBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(HawklyxEmerald.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = HawklyxEmerald,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "How to add the widget",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = HawklyxTextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Long-press your home screen, select Widgets, locate HAWKLYX, and place the 3×2 widget.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HawklyxTextPrimary
                        )
                    )
                }
            }
        }

        // Section: Architecture & Zero-Trust Security
        AboutSectionCard(
            title = "EDGE ARCHITECTURE & SECURITY",
            icon = Icons.Default.Layers
        ) {
            Text(
                text = "HAWKLYX enforces a zero-trust client model. Neither the Android app nor the widget stores API tokens or upstream keys.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    color = HawklyxTextSecondary
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Requests route through a resilient Cloudflare Worker coordinated by a Durable Object. The coordinator manages an authorized multi-account pool, request coalescing, strict price sanity bounds ($10 to $250/bbl), and fail-closed security.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    color = HawklyxTextSecondary
                )
            )
        }

        // Section: Cadence & Policies (Redesigned responsive layout, quota row removed)
        AboutSectionCard(
            title = "REFRESH CADENCE & QUOTA POLICIES",
            icon = Icons.Default.Speed
        ) {
            InformationRow(
                label = "Background Cadence",
                value = "15-minute schedule"
            )
            InformationRow(
                label = "Manual Refresh Limit",
                value = "3-minute cooldown"
            )
            InformationRow(
                label = "Offline Fallback",
                value = "Last-known-good persistence",
                showDivider = false
            )
        }

        // Section: Market Specifications (Stacked responsive layout matching Refresh Cadence)
        AboutSectionCard(
            title = "MARKET SPECIFICATIONS",
            icon = Icons.Default.Assessment
        ) {
            InformationRow(
                label = "Commodity Benchmark",
                value = observation?.name ?: "Brent Crude Oil"
            )
            InformationRow(
                label = "Contract Symbol",
                value = observation?.code ?: "BRENT_CRUDE_USD"
            )
            InformationRow(
                label = "Denomination",
                value = "USD per barrel (bbl)"
            )
            InformationRow(
                label = "Edge Upstream Source",
                value = observation?.source ?: "OilPriceAPI"
            )
            InformationRow(
                label = "Edge Coordination",
                value = "Cloudflare Durable Object"
            )
            InformationRow(
                label = "Refresh Schedule",
                value = "15-min cadence (3-min cooldown)",
                showDivider = false
            )
        }

        // Section: Project & Developer Links (Simplified titles with matching trailing icons)
        AboutSectionCard(
            title = "DEVELOPER & PROJECT LINKS",
            icon = Icons.Default.Code
        ) {
            Text(
                text = "Explore the creator's portfolio and the open-source GitHub repository below:",
                style = MaterialTheme.typography.bodyMedium.copy(color = HawklyxTextSecondary)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Portfolio Link Card
            ExternalLinkTile(
                icon = Icons.Default.Language,
                title = "Portfolio",
                url = "https://alifaniani.ir",
                onClick = { uriHandler.openUri("https://alifaniani.ir") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // GitHub Repository Link Card
            ExternalLinkTile(
                icon = Icons.Default.Code,
                title = "GitHub",
                url = "https://github.com/Greenhawk5/HAWKLYX",
                onClick = { uriHandler.openUri("https://github.com/Greenhawk5/HAWKLYX") }
            )
        }

        // Section: Technical Specifications (Matching stacked responsive layout)
        AboutSectionCard(
            title = "SYSTEM SPECIFICATIONS",
            icon = Icons.Default.Security
        ) {
            InformationRow(
                label = "Application Version",
                value = "1.0 (Build 1)"
            )
            InformationRow(
                label = "Application ID",
                value = "ir.alifaniani.hawklyx"
            )
            InformationRow(
                label = "Target Android SDK",
                value = "Android 15 (API 37)"
            )
            InformationRow(
                label = "Minimum Android SDK",
                value = "Android 8.0 (API 26)"
            )
            InformationRow(
                label = "Data Provider",
                value = "OilPriceAPI (Personal License)",
                showDivider = false
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun HeroBrandCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Direct prominent falcon artwork without redundant outer icon container
        Image(
            painter = painterResource(id = R.drawable.hawklyx_logo),
            contentDescription = "HAWKLYX Falcon Brand Mark",
            modifier = Modifier.size(128.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "HAWKLYX",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = HawklyxTextPrimary
            )
        )

        Text(
            text = "Brent Crude Price Monitor",
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 1.sp,
                color = HawklyxEmerald
            )
        )
    }
}

@Composable
private fun AboutSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HawklyxSurface)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(HawklyxSurfaceVariant),
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
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = HawklyxTextSecondary
                )
            )
        }

        HorizontalDivider(color = HawklyxBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        content()
    }
}

@Composable
private fun InformationRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = HawklyxTextMuted,
                    fontWeight = FontWeight.Normal
                )
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
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

@Composable
private fun ExternalLinkTile(
    icon: ImageVector,
    title: String,
    url: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HawklyxSurfaceVariant)
            .border(1.dp, HawklyxBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HawklyxEmerald,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = HawklyxTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    color = HawklyxTextMuted
                )
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open $title in browser",
            tint = HawklyxTextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
