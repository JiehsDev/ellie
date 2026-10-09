package com.example.jikan.ui.home

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.Session
import com.example.jikan.data.ThemeMode
import com.example.jikan.data.UserSettings
import com.example.jikan.data.Wallet
import com.example.jikan.onboarding.PermissionStatus
import com.example.jikan.service.PauseManager
import com.example.jikan.service.StrictModeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One bar in Home's week strip: how many minutes were studied on that day. */
data class StudyDay(
    val initial: String,
    val minutes: Int,
    val isToday: Boolean,
)

data class LockedAppChip(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

data class HomeUiState(
    val userName: String = "",
    val walletBalanceMinutes: Int = 0,
    val maxBalanceMinutes: Int = Wallet.MAX_BALANCE_MINUTES,
    val streakDays: Int = 0,
    val cardsLearned: Int = 0,
    val totalCards: Int = 0,
    val dueCount: Int = 0,
    val minutesSpentToday: Int = 0,
    val lockedApps: List<LockedAppChip> = emptyList(),
    val lockedAppCount: Int = 0,
    val week: List<StudyDay> = emptyList(),
    val protectionOn: Boolean = true,
    val batteryOptimizationsIgnored: Boolean = true,
    val bankingModeStatus: BankingModeStatus = BankingModeStatus.Idle,
    val coachMessage: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val strictModeEnabled: Boolean = false,
    val strictGraceUntilMs: Long = 0L,
    val bankingDisabledUntilMs: Long = 0L,
    val bankingModeActive: Boolean = false,
    val lastViolationAtMs: Long = 0L,
    val isLoading: Boolean = true,
)

enum class BankingModeStatus {
    Idle,
    Selecting,
    Loading,
    Success,
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refreshProtectionStatus()
        observeStats()
        observeLockedApps()
    }

    private fun observeStats() {
        viewModelScope.launch {
            val totalCards = db.cardDao().count()
            val now = System.currentTimeMillis()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

            val core = combine(
                db.walletDao().observe(),
                db.progressDao().observeAll(),
                db.progressDao().observeDueCount(now),
                db.settingsDao().observe(),
                db.lockedAppDao().observeLocked(),
            ) { wallet, progress, dueCount, settings, lockedApps ->
                CoreSnapshot(
                    wallet = wallet,
                    cardsLearned = progress.size,
                    dueCount = dueCount,
                    userName = settings?.userName.orEmpty(),
                    themeMode = settings?.themeMode ?: ThemeMode.SYSTEM,
                    strictModeEnabled = settings?.strictModeEnabled ?: false,
                    strictGraceUntilMs = settings?.strictGraceUntilMs ?: 0L,
                    bankingDisabledUntilMs = settings?.bankingDisabledUntilMs ?: 0L,
                    bankingModeActive = settings?.bankingModeActive ?: false,
                    lastViolationAtMs = settings?.lastViolationAtMs ?: 0L,
                    lockedAppCount = lockedApps.size,
                )
            }

            combine(
                core,
                db.appUsageDao().observeTotalMinutes(today),
                db.sessionDao().observeAll(),
            ) { snapshot, spentToday, sessions ->
                Triple(snapshot, spentToday, sessions)
            }.collect { (snapshot, spentToday, sessions) ->
                _state.update { current ->
                    val week = buildWeek(sessions)
                    val studiedToday = studyMinutesForDay(sessions, LocalDate.now(zone))
                    val studiedYesterday = studyMinutesForDay(sessions, LocalDate.now(zone).minusDays(1))
                    val updated = current.copy(
                        userName = snapshot.userName,
                        themeMode = snapshot.themeMode,
                        strictModeEnabled = snapshot.strictModeEnabled,
                        strictGraceUntilMs = snapshot.strictGraceUntilMs,
                        bankingDisabledUntilMs = snapshot.bankingDisabledUntilMs,
                        bankingModeActive = snapshot.bankingModeActive,
                        lastViolationAtMs = snapshot.lastViolationAtMs,
                        walletBalanceMinutes = snapshot.wallet?.creditBalanceMinutes ?: 0,
                        streakDays = snapshot.wallet?.currentStreakDays ?: 0,
                        cardsLearned = snapshot.cardsLearned,
                        totalCards = totalCards,
                        dueCount = snapshot.dueCount,
                        minutesSpentToday = spentToday,
                        lockedAppCount = snapshot.lockedAppCount,
                        week = week,
                        isLoading = false,
                    )
                    updated.copy(
                        coachMessage = coachMessageFor(
                            state = updated,
                            studiedTodayMinutes = studiedToday,
                            studiedYesterdayMinutes = studiedYesterday,
                        )
                    )
                }
            }
        }
    }

    /**
     * Icons come from PackageManager, which is slow enough to visibly stall the whole
     * screen if it shares a collector with the stats. Names land first, artwork follows.
     */
    private fun observeLockedApps() {
        viewModelScope.launch {
            db.lockedAppDao().observeLocked().collect { apps ->
                val placeholders = apps.map { LockedAppChip(it.packageName, it.appLabel, null) }
                _state.update { it.copy(lockedApps = placeholders) }
                val withIcons = loadIcons(placeholders)
                _state.update { it.copy(lockedApps = withIcons) }
            }
        }
    }

    /**
     * Re-read on every resume rather than observed: the user leaves the app entirely
     * to toggle the accessibility service, so there is nothing to subscribe to.
     */
    fun refreshProtectionStatus() {
        viewModelScope.launch {
            StrictModeManager.reconcile(getApplication())
            val enabled = PermissionStatus.isAccessibilityServiceEnabled(getApplication())
            PauseManager.clearBankingModeIfProtectionEnabled(getApplication(), enabled)
            val batteryIgnored = PermissionStatus.isIgnoringBatteryOptimizations(getApplication())
            _state.update {
                val updated = it.copy(protectionOn = enabled, batteryOptimizationsIgnored = batteryIgnored)
                updated.copy(coachMessage = coachMessageFor(updated))
            }
        }
    }

    fun disableLockingForBanking() {
        _state.update {
            val updated = it.copy(bankingModeStatus = BankingModeStatus.Selecting)
            updated.copy(coachMessage = coachMessageFor(updated))
        }
    }

    fun startBankingAllowlist(packageName: String, appLabel: String, durationMinutes: Int) {
        startFullDisableBankingMode(durationMinutes)
    }

    fun startFullDisableBankingMode(durationMinutes: Int) {
        _state.update {
            val updated = it.copy(bankingModeStatus = BankingModeStatus.Loading)
            updated.copy(coachMessage = coachMessageFor(updated))
        }
        viewModelScope.launch {
            PauseManager.requestAccessibilityDisable(getApplication(), durationMinutes)
            delay(1_200L)
            refreshProtectionStatus()
            _state.update {
                val updated = it.copy(bankingModeStatus = BankingModeStatus.Success)
                updated.copy(coachMessage = coachMessageFor(updated))
            }
        }
    }

    fun dismissBankingModeStatus() {
        _state.update {
            val updated = it.copy(bankingModeStatus = BankingModeStatus.Idle)
            updated.copy(coachMessage = coachMessageFor(updated))
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            val current = db.settingsDao().get() ?: UserSettings()
            db.settingsDao().upsert(current.copy(themeMode = themeMode))
        }
    }

    private suspend fun loadIcons(chips: List<LockedAppChip>): List<LockedAppChip> =
        withContext(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            chips.map { chip ->
                chip.copy(icon = runCatching { pm.getApplicationIcon(chip.packageName) }.getOrNull())
            }
        }

    /** Study minutes per day for the trailing week, oldest first. */
    private fun buildWeek(sessions: List<Session>): List<StudyDay> {
        val today = LocalDate.now(zone)
        val minutesByDay = sessions
            .filter { it.completedAt != null }
            .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .mapValues { (_, daySessions) ->
                daySessions.sumOf { session ->
                    val elapsed = (session.completedAt ?: session.startedAt) - session.startedAt
                    (elapsed / 60_000L).toInt().coerceAtLeast(1)
                }
            }

        return (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            StudyDay(
                initial = date.dayOfWeek.name.take(1),
                minutes = minutesByDay[date] ?: 0,
                isToday = daysAgo == 0,
            )
        }
    }

    private fun studyMinutesForDay(sessions: List<Session>, date: LocalDate): Int {
        return sessions
            .filter { it.completedAt != null }
            .filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == date }
            .sumOf { session ->
                val elapsed = (session.completedAt ?: session.startedAt) - session.startedAt
                (elapsed / 60_000L).toInt().coerceAtLeast(1)
            }
    }

    private fun coachMessageFor(
        state: HomeUiState,
        studiedTodayMinutes: Int = state.week.firstOrNull { it.isToday }?.minutes ?: 0,
        studiedYesterdayMinutes: Int = state.week.lastOrNull { !it.isToday }?.minutes ?: 0,
    ): String {
        return HomeCoachMessageProvider.messageFor(
            CoachInput(
                userName = state.userName,
                walletBalanceMinutes = state.walletBalanceMinutes,
                streakDays = state.streakDays,
                dueCount = state.dueCount,
                studiedTodayMinutes = studiedTodayMinutes,
                studiedYesterdayMinutes = studiedYesterdayMinutes,
                protectionOn = state.protectionOn,
                bankingModeActive = state.bankingModeStatus == BankingModeStatus.Success && !state.protectionOn,
                hourOfDay = Instant.now().atZone(zone).hour,
            )
        )
    }

    private data class CoreSnapshot(
        val wallet: Wallet?,
        val cardsLearned: Int,
        val dueCount: Int,
        val userName: String,
        val themeMode: ThemeMode,
        val strictModeEnabled: Boolean,
        val strictGraceUntilMs: Long,
        val bankingDisabledUntilMs: Long,
        val bankingModeActive: Boolean,
        val lastViolationAtMs: Long,
        val lockedAppCount: Int,
    )
}
