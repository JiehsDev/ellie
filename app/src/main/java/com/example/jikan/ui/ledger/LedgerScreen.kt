package com.example.jikan.ui.ledger

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.R
import com.example.jikan.data.Wallet
import com.example.jikan.data.WalletTransaction
import com.example.jikan.data.WalletTransactionType
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stitch reference: the full Time Balance ledger. Tabbed history
 * (Today / 7 days / month), depleted-state alert, filterable day-grouped
 * entries, and CSV export. All from the real transaction table.
 */
@Composable
fun LedgerScreen(
    onBack: () -> Unit,
    onQuickBurst: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LedgerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    fun shareCsv() {
        val csv = viewModel.exportCsv()
        val file = File(context.cacheDir, "jikan-time-logs.csv")
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export time logs"))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 28.dp),
    ) {
        // Header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Time Balance & Allowance",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.mascot_jikan_coach),
                contentDescription = "Ellie",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Spacer(Modifier.height(14.dp))

        // Range tabs + sync.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RangeTab("Today", state.range == LedgerRange.TODAY) { viewModel.setRange(LedgerRange.TODAY) }
            Spacer(Modifier.width(8.dp))
            RangeTab("Past 7 Days", state.range == LedgerRange.WEEK) { viewModel.setRange(LedgerRange.WEEK) }
            Spacer(Modifier.width(8.dp))
            RangeTab("This Month", state.range == LedgerRange.MONTH) { viewModel.setRange(LedgerRange.MONTH) }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Sync: Local",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Balance card.
        BalanceCard(state = state)

        // Depleted alert.
        if (state.balanceMinutes <= 0) {
            Spacer(Modifier.height(12.dp))
            DepletedCard(state = state, onQuickBurst = onQuickBurst)
        }

        Spacer(Modifier.height(16.dp))

        // Filters.
        Row(modifier = Modifier.fillMaxWidth()) {
            FilterChip("All", state.totalCount, state.filter == LedgerFilter.ALL) {
                viewModel.setFilter(LedgerFilter.ALL)
            }
            Spacer(Modifier.width(8.dp))
            FilterChip("Earned", state.earnedCount, state.filter == LedgerFilter.EARNED) {
                viewModel.setFilter(LedgerFilter.EARNED)
            }
            Spacer(Modifier.width(8.dp))
            FilterChip("Used", state.usedCount, state.filter == LedgerFilter.USED) {
                viewModel.setFilter(LedgerFilter.USED)
            }
            Spacer(Modifier.width(8.dp))
            FilterChip("Adjustments", state.adjustmentCount, state.filter == LedgerFilter.ADJUSTMENTS) {
                viewModel.setFilter(LedgerFilter.ADJUSTMENTS)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Day groups.
        if (state.days.isEmpty()) {
            if (state.filter == LedgerFilter.ADJUSTMENTS) {
                NoAdjustmentsCard()
            } else {
                Text(
                    text = "No entries in this range yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        } else {
            state.days.forEach { day ->
                DayGroup(day = day)
                Spacer(Modifier.height(16.dp))
            }
            if (state.filter == LedgerFilter.ADJUSTMENTS && state.adjustmentCount == 0) {
                NoAdjustmentsCard()
            }
        }

        Spacer(Modifier.height(8.dp))

        // Export.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = ::shareCsv)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Upload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Export time logs (CSV)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "All usage logs are stored locally on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RangeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) MaterialTheme.colorScheme.onSecondary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun BalanceCard(state: LedgerUiState) {
    val depleted = state.balanceMinutes <= 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "AVAILABLE BALANCE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            if (depleted) {
                Text(
                    text = "● Depleted",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
                Text(
                    text = "${state.balanceMinutes}",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (depleted) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                )
                Text(
                    text = "m",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    text = " /${Wallet.MAX_BALANCE_MINUTES}m daily cap",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Text(
                text = "Earned today: +${state.earnedTodayMinutes}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun DepletedCard(state: LedgerUiState, onQuickBurst: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        // Terracotta left accent.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Balance Depleted • ${state.balanceMinutes}m remaining",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "All ${state.lockedAppCount} leisure apps are currently locked. " +
                            "Your next scheduled spaced review is ready to unlock fresh screen time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondary)
                            .clickable(onClick = onQuickBurst)
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Study 5 cards to unlock +${quickBurstReward()}m",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Work & essential tools stay active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun quickBurstReward(): Int =
    com.example.jikan.study.CreditCalculator.creditsEarned(correctCount = 4, totalCount = 5)

@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSecondary
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSecondary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surface
                )
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DayGroup(day: LedgerDay) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Net: ${if (day.netMinutes >= 0) "+" else ""}${day.netMinutes}m",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        day.transactions.forEach { tx ->
            LedgerEntryRow(tx = tx)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun LedgerEntryRow(tx: WalletTransaction) {
    val isEarn = tx.type == WalletTransactionType.EARN
    val time = Instant.ofEpochMilli(tx.createdAtMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isEarn) Icons.Filled.School else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.note ?: tx.packageName ?: "Wallet update",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (!isEarn && tx.packageName != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Leisure",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = tx.note?.let { tx.packageName } ?: if (isEarn) "Active Recall" else "Allowance spent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (isEarn) "+" else "-"}${tx.minutes}m",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isEarn) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoAdjustmentsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Spa,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No penalty adjustments",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ellie operates on gentle positive reinforcement. Credits are never revoked as penalties — only used through leisure apps or refreshed on daily reset.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
    }
}
