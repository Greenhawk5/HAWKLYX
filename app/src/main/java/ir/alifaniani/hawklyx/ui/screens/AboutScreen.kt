package ir.alifaniani.hawklyx.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        // Navigation Header
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
                    contentDescription = "Back to Home",
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

        // Section: Product Philosophy
        AboutCard(
            title = "PRODUCT PHILOSOPHY",
            icon = Icons.Default.Shield
        ) {
            Text(
                text = "HAWKLYX combines Minimal Precision with Premium Finance. The Android home-screen widget is the core experience, built to present accurate Brent crude oil price observations at a glance without unnecessary clutter or dashboard bloat.",
                style = MaterialTheme.typography.bodyMedium,
                color = HawklyxTextSecondary,
                lineHeight = 22.sp
            )
        }

        // Section: Edge Architecture & Data Resilience
        AboutCard(
            title = "ARCHITECTURE & TRANSPARENCY",
            icon = Icons.Default.CloudQueue
        ) {
            Text(
                text = "HAWKLYX implements a zero-trust client architecture. The Android application and widget never hold external API secrets directly.",
                style = MaterialTheme.typography.bodyMedium,
                color = HawklyxTextSecondary,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Data is routed through a resilient Cloudflare Worker edge service which coordinates an authorized multi-account key pool (50 requests/account daily quota, approved by OilPriceAPI support for personal non-commercial use), server-side cooldown, request coalescing, strict anomaly filtering, and last-known-good offline persistence.",
                style = MaterialTheme.typography.bodyMedium,
                color = HawklyxTextSecondary,
                lineHeight = 22.sp
            )
        }

        // Section: Developer & Contact
        AboutCard(
            title = "CONTACT & ATTRIBUTION",
            icon = Icons.Default.Email
        ) {
            InfoLine(label = "Developer", value = "Ali Faniani")
            InfoLine(label = "Contact Email", value = "ali.faniani@gmail.com")
            InfoLine(label = "Data Provider", value = "OilPriceAPI (Personal / Educational)")
            InfoLine(label = "License", value = "Personal, Non-Commercial Use")
        }

        // Section: Version & System Details
        AboutCard(
            title = "APPLICATION DETAILS",
            icon = Icons.Default.Security
        ) {
            InfoLine(label = "Application Version", value = "1.0 (Build 1)")
            InfoLine(label = "Package ID", value = "ir.alifaniani.hawklyx")
            InfoLine(label = "Target SDK", value = "Android 15 (API 37)")
            InfoLine(label = "Minimum SDK", value = "Android 8.0 (API 26)")
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun AboutCard(
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
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
