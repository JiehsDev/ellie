package com.example.jikan.ui.habits

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoonStar
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.R
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.Session
import com.example.jikan.data.WalletTransaction
import com.example.jikan.data.WalletTransactionSource
import com.example.jikan.data.WalletTransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class HistoryFilter { ALL, SESSIONS, REST }

data class LedgerEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val creditLabel: String?,
    val icon: LedgerIcon,
    val epochDay: Long,
    val timestampMs: Long,
)

enum class LedgerIcon { FLASHCARDS, LANGUAGE, MANUAL, REST }

data class DayGroup(
    val date: LocalDate,
    val headerLabel: String,
    val summaryLabel: String,
    val entries: List<LedgerEntry>,
    val isRestDay: Boolean,
)

data class WeekFlow(
    val label: String,
    val dateRange: String,
    val activeDays: Int,
    val minutes: Int,
)

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val practiceTimeText: String = "",
    val habitCount: Int = 0,
    val sessionCount: Int = 0,
    val avgPerDayText: String = "",
    val consistencyPercent: Int = 0,
    val consistencyText: String = "",
    val earnedText: String = "",
    val weeks: List<WeekFlow> = emptyList(),
    val restDays: List<LocalDate> = emptyList(),
    val dayGroups: List<DayGroup> = emptyList(),
    val periodLabel: String = "",
    val isLoading: Boolean = true,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone = ZoneId.systemDefault()
    private val periodDays = 28

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init { refresh() }

    fun setFilter(filter: HistoryFilter) {
        _state.update { it.copy(filter = filter) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now(zone)
            val startDate = today.minusDays(periodDays - 1L)
            val startMs = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val sessions = db.sessionDao().getAllOnce().filter { it.startedAt >= startMs }
            val transactions = db.walletTransactionDao().observeAll().first().filter {
                it.type == WalletTransactionType.EARN &&
                    it.epochDay >= startDate.toEpochDay()
            }
            val earnRules = db.earnRuleDao().observeAll().first()

            val filter = _state.value.filter

            // Build ledger entries.
            val entries = mutableListOf<LedgerEntry>()
            sessions.forEach { s ->
                val date = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
                val timeStr = Instant.ofEpochMilli(s.startedAt).atZone(zone)
                    .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
                entries.add(
                    LedgerEntry(
                        id = "s${s.id}",
                        title = "Japanese N3 Kanji",
                        subtitle = "$timeStr • ${s.questionsTotal} cards (${s.durationMinutes}m)",
                        creditLabel = "+${s.creditsEarned}m credit",
                        icon = if (s.triggeredByPackage == null && s.questionsTotal == s.durationMinutes * 2)
                            LedgerIcon.MANUAL else LedgerIcon.FLASHCARDS,
                        epochDay = date.toEpochDay(),
                        timestampMs = s.startedAt,
                    )
                )
            }
            transactions.filter { it.source == WalletTransactionSource.EARNING_APP }.forEach { t ->
                val rule = earnRules.find { it.id == t.ruleId }
                val appName = rule?.label?.ifBlank { null }
                    ?: rule?.packageName?.split(".")?.lastOrNull()?.replaceFirstChar { c -> c.uppercase() }
                    ?: "Practice"
                entries.add(
                    LedgerEntry(
                        id = "t${t.id}",
                        title = "$appName Practice",
                        subtitle = "${t.qualifyingMinutes}m lesson",
                        creditLabel = "+${t.minutes}m credit",
                        icon = LedgerIcon.LANGUAGE,
                        epochDay = t.epochDay,
                        timestampMs = t.createdAtMs,
                    )
                )
            }

            // Group by day.
            val byDay = entries.groupBy { it.epochDay }
            val activeDays = byDay.keys.map { LocalDate.ofEpochDay(it) }.toSet()

            // Rest days (past days with no activity).
            val restDays = (0 until periodDays).map { i -> startDate.plusDays(i.toLong()) }
                .filter { it.isBefore(today) && it !in activeDays }

            // Day groups (filtered).
            val dayGroups = (0 until periodDays).map { i ->
                val date = today.minusDays(i.toLong())
                val dayEntries = (byDay[date.toEpochDay()] ?: emptyList())
                    .sortedByDescending { it.timestampMs }
                val isRest = date.isBefore(today) && dayEntries.isEmpty()
                val include = when (filter) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.SESSIONS -> dayEntries.isNotEmpty()
                    HistoryFilter.REST -> isRest
                }
                if (!include) return@map null
                val dayMinutes = dayEntries.sumOf {
                    it.subtitle.substringAfter("(").substringBefore("m").toIntOrNull() ?: 0
                }
                val headerFmt = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
                val header = date.format(headerFmt).uppercase(Locale.US)
                val summary = when {
                    date == today -> "Today • ${dayMinutes}m total study"
                    date == today.minusDays(1) -> "Yesterday • ${dayMinutes}m total study"
                    isRest -> "Scheduled Pause"
                    else -> "${dayMinutes}m total study"
                }
                DayGroup(
                    date = date,
                    headerLabel = header,
                    summaryLabel = summary,
                    entries = dayEntries,
                    isRestDay = isRest,
                )
            }.filterNotNull()

            // Stats.
            val totalMinutes = sessions.sumOf { it.durationMinutes } +
                transactions.filter { it.source == WalletTransactionSource.EARNING_APP }
                    .sumOf { it.qualifyingMinutes }
            val habitCount = 1 + earnRules.count { it.enabled }
            val activeDayCount = activeDays.size
            val consistencyPct = if (periodDays > 0) activeDayCount * 100 / periodDays else 0
            val earnedMinutes = sessions.sumOf { it.creditsEarned } +
                transactions.sumOf { it.minutes }

            // Weekly flow.
            val weeks = (0 until 4).map { w ->
                val weekStart = startDate.plusDays(w * 7L)
                val weekEnd = weekStart.plusDays(6)
                val weekDays = (0 until 7).map { weekStart.plusDays(it.toLong()) }
                val weekActive = weekDays.count { it in activeDays }
                val weekMinutes = sessions.filter {
                    val d = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
                    !d.isBefore(weekStart) && !d.isAfter(weekEnd)
                }.sumOf { it.durationMinutes }
                val rangeFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
                WeekFlow(
                    label = "Week ${w + 1}",
                    dateRange = "${weekStart.format(rangeFmt)}–${weekEnd.format(rangeFmt)}",
                    activeDays = weekActive,
                    minutes = weekMinutes,
                )
            }

            val periodFmt = DateTimeFormatter.ofPattern("MMMM d", Locale.US)
            _state.update {
                it.copy(
                    practiceTimeText = formatHours(totalMinutes),
                    habitCount = habitCount,
                    sessionCount = sessions.size,
                    avgPerDayText = if (activeDayCount > 0) "~${totalMinutes / activeDayCount}m / active day" else "—",
                    consistencyPercent = consistencyPct,
                    consistencyText = "$activeDayCount of $periodDays days verified",
                    earnedText = formatHours(earnedMinutes),
                    weeks = weeks,
                    restDays = restDays,
                    dayGroups = dayGroups,
                    periodLabel = "${startDate.format(periodFmt)} – ${today.format(periodFmt)}, ${today.year}",
                    isLoading = false,
                )
            }
        }
    }

    private fun formatHours(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun exportCsv(): String {
        val s = _state.value
        val header = "date,title,subtitle,credit"
        val rows = s.dayGroups.flatMap { g ->
            if (g.entries.isEmpty()) {
                listOf("${g.date},Rest day,,")
            } else {
                g.entries.map { e ->
                    "${g.date},\"${e.title}\",\"${e.subtitle}\",\"${e.creditLabel ?: ""}\""
                }
            }
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    fun shareCsv() {
        val csv = viewModel.exportCsv()
        val file = File(context.cacheDir, "jikan-history.csv")
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export history"))
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
                text = "Session Log History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.mascot_jikan_coach),
                contentDescription = "JikanCoach",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Historical Activity & Ledger",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "● LIVE SYNC",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = state.periodLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Filters.
        Row(modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                label = "All Activity (28d)",
                selected = state.filter == HistoryFilter.ALL,
                onClick = { viewModel.setFilter(HistoryFilter.ALL) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                label = "Completed Sessions",
                selected = state.filter == HistoryFilter.SESSIONS,
                onClick = { viewModel.setFilter(HistoryFilter.SESSIONS) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                label = "Rest",
                selected = state.filter == HistoryFilter.REST,
                onClick = { viewModel.setFilter(HistoryFilter.REST) },
            )
        }

        Spacer(Modifier.height(14.dp))

        // Stats 2x2.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    icon = Icons.Filled.Timer,
                    label = "PRACTICE TIME",
                    value = state.practiceTimeText,
                    sub = "${state.habitCount} active habits backed",
                    progress = state.consistencyPercent / 100f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                StatTile(
                    icon = Icons.Filled.Check,
                    label = "SESSIONS",
                    value = "${state.sessionCount}",
                    sub = state.avgPerDayText,
                    progress = state.consistencyPercent / 100f,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    icon = Icons.Filled.GridView,
                    label = "CONSISTENCY",
                    value = "${state.consistencyPercent}%",
                    sub = state.consistencyText,
                    progress = state.consistencyPercent / 100f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                StatTile(
                    icon = Icons.Filled.Lock,
                    label = "EARNED SCREEN",
                    value = state.earnedText,
                    sub = "100% habit funded",
                    progress = 1f,
                    valueColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Weekly flow.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Weekly Flow & Natural Cadence",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Sustainable momentum without burnout pressure",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.QueryStats,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            state.weeks.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.width(72.dp)) {
                        Text(
                            text = week.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = week.dateRange,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${week.activeDays}/7 days active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (week.activeDays / 7f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatMinutes(week.minutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Rest days.
        if (state.restDays.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Spa,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${state.restDays.size} Mindful Rest Days Observed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Zero Decay",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val restFmt = DateTimeFormatter.ofPattern("MMMM d", Locale.US)
                Text(
                    text = state.restDays.joinToString(", ") { it.format(restFmt) } +
                        " were logged as voluntary rest pauses. Spaced repetition decay algorithms " +
                        "adjusted naturally without cognitive penalty or artificial urgency.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // Ledger stream.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ledger Stream",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Descending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        state.dayGroups.forEach { group ->
            Text(
                text = group.headerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = group.summaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (group.isRestDay) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoonStar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mindful Recovery Day",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Zero scheduled obligations • Quiet hours maintained",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Protected Rest",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            } else {
                group.entries.forEach { entry ->
                    LedgerRow(entry = entry)
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Reflection.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_jikan_coach),
                contentDescription = "JikanCoach",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Panda Reflection • Me & Harmony",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "\"Balance over burnout. Sustainable rhythms survive because of rest, not in spite of it.\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 21.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Export.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondary)
                .clickable(onClick = ::shareCsv)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Export Complete Activity History (.csv)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondary,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Encrypted locally in device SQLite ledger • Zero remote sync tracking",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    sub: String,
    progress: Float,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntry) {
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
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (entry.icon) {
                    LedgerIcon.FLASHCARDS -> Icons.Filled.School
                    LedgerIcon.LANGUAGE -> Icons.Filled.Translate
                    LedgerIcon.MANUAL -> Icons.Filled.HourglassEmpty
                    LedgerIcon.REST -> Icons.Filled.MoonStar
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.creditLabel != null) {
            Text(
                text = entry.creditLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
