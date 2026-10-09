package com.example.jikan.ui.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.WalletTransaction
import com.example.jikan.data.WalletTransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class LedgerRange { TODAY, WEEK, MONTH }
enum class LedgerFilter { ALL, EARNED, USED, ADJUSTMENTS }

data class LedgerDay(
    val date: LocalDate,
    val label: String,
    val netMinutes: Int,
    val transactions: List<WalletTransaction>,
)

data class LedgerUiState(
    val range: LedgerRange = LedgerRange.TODAY,
    val filter: LedgerFilter = LedgerFilter.ALL,
    val balanceMinutes: Int = 0,
    val earnedTodayMinutes: Int = 0,
    val lockedAppCount: Int = 0,
    val days: List<LedgerDay> = emptyList(),
    val earnedCount: Int = 0,
    val usedCount: Int = 0,
    val adjustmentCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val zone = ZoneId.systemDefault()

    private val _state = MutableStateFlow(LedgerUiState())
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                db.walletTransactionDao().observeAll(),
                db.walletDao().observe(),
                db.lockedAppDao().observeLocked(),
            ) { transactions, wallet, lockedApps ->
                Triple(transactions, wallet, lockedApps.size)
            }.collect { (transactions, wallet, lockedCount) ->
                val today = LocalDate.now(zone)
                val todayTx = transactions.filter {
                    it.epochDay == today.toEpochDay()
                }
                _state.update {
                    it.copy(
                        balanceMinutes = wallet?.creditBalanceMinutes ?: 0,
                        earnedTodayMinutes = todayTx
                            .filter { t -> t.type == WalletTransactionType.EARN }
                            .sumOf { t -> t.minutes },
                        lockedAppCount = lockedCount,
                        isLoading = false,
                    )
                }
                rebuild(transactions)
            }
        }
    }

    fun setRange(range: LedgerRange) {
        _state.update { it.copy(range = range) }
        rebuild()
    }

    fun setFilter(filter: LedgerFilter) {
        _state.update { it.copy(filter = filter) }
        rebuild()
    }

    private var lastTransactions: List<WalletTransaction> = emptyList()

    private fun rebuild(transactions: List<WalletTransaction> = lastTransactions) {
        lastTransactions = transactions
        val state = _state.value
        val today = LocalDate.now(zone)
        val startDay = when (state.range) {
            LedgerRange.TODAY -> today
            LedgerRange.WEEK -> today.minusDays(6)
            LedgerRange.MONTH -> today.withDayOfMonth(1)
        }
        val inRange = transactions.filter { it.epochDay >= startDay.toEpochDay() }
        val filtered = when (state.filter) {
            LedgerFilter.ALL -> inRange
            LedgerFilter.EARNED -> inRange.filter { it.type == WalletTransactionType.EARN }
            LedgerFilter.USED -> inRange.filter { it.type == WalletTransactionType.SPEND }
            LedgerFilter.ADJUSTMENTS -> inRange.filter {
                it.type == WalletTransactionType.MANUAL_ADJUSTMENT ||
                    it.type == WalletTransactionType.EXPIRATION
            }
        }
        val days = filtered.groupBy { it.epochDay }
            .toSortedMap(compareByDescending { it })
            .map { (epochDay, txs) ->
                val date = LocalDate.ofEpochDay(epochDay)
                val net = txs.sumOf {
                    if (it.type == WalletTransactionType.EARN) it.minutes else -it.minutes
                }
                val label = when (date) {
                    today -> "TODAY"
                    today.minusDays(1) -> "YESTERDAY"
                    else -> date.dayOfWeek.name.lowercase()
                        .replaceFirstChar { c -> c.uppercase() }
                }
                val dateStr = date.format(
                    java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)
                ).uppercase()
                LedgerDay(
                    date = date,
                    label = "$label ($dateStr)",
                    netMinutes = net,
                    transactions = txs.sortedByDescending { it.createdAtMs },
                )
            }
        _state.update {
            it.copy(
                days = days,
                earnedCount = inRange.count { t -> t.type == WalletTransactionType.EARN },
                usedCount = inRange.count { t -> t.type == WalletTransactionType.SPEND },
                adjustmentCount = inRange.count { t ->
                    t.type == WalletTransactionType.MANUAL_ADJUSTMENT ||
                        t.type == WalletTransactionType.EXPIRATION
                },
                totalCount = inRange.size,
            )
        }
    }

    /** CSV export of the visible transactions. Returns the file content. */
    fun exportCsv(): String {
        val header = "date,time,type,minutes,balance_after,note,package"
        val rows = lastTransactions.sortedByDescending { it.createdAtMs }.map { tx ->
            val dt = Instant.ofEpochMilli(tx.createdAtMs).atZone(zone)
            val date = dt.toLocalDate().toString()
            val time = dt.toLocalTime().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            )
            val note = (tx.note ?: "").replace(",", ";").replace("\n", " ")
            "$date,$time,${tx.type},${tx.minutes},${tx.balanceAfter},$note,${tx.packageName ?: ""}"
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}
