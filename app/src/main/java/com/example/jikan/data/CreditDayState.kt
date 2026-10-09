package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * Per-day credit accounting state. One row per local policy day.
 *
 * - [validatedStudyMinutes]: cumulative validated study minutes today.
 *   Fractional earning progress is preserved implicitly: credits are
 *   computed incrementally from this total via CreditPolicy, so repeated
 *   short sessions cannot gain extra credits through rounding.
 * - [allowanceUsedMinutes]: recreational minutes consumed today, shared
 *   across all restricted apps.
 * - [profileName]: the CreditProfile active when this day started. If the
 *   user changes profile mid-day, earning tiers apply from the new profile
 *   going forward; already-earned credits are never revoked.
 * - [lastClockMs]: wall-clock time of the last update, used to detect
 *   suspicious clock changes (allowance cannot be reset by turning the
 *   clock back).
 */
@Entity(tableName = "credit_day_state")
data class CreditDayState(
    @PrimaryKey val epochDay: Long,
    val validatedStudyMinutes: Int = 0,
    val allowanceUsedMinutes: Int = 0,
    val profileName: String = "BALANCED",
    val lastClockMs: Long = 0L,
)

@Dao
interface CreditDayStateDao {
    @Query("SELECT * FROM credit_day_state WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): CreditDayState?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(state: CreditDayState)

    @Update
    suspend fun update(state: CreditDayState)

    /**
     * Atomically adds validated minutes. Returns the updated total.
     * Uses a transaction to prevent concurrent sessions from double-counting.
     */
    @Query(
        "UPDATE credit_day_state SET validatedStudyMinutes = validatedStudyMinutes + :minutes, " +
            "lastClockMs = :nowMs WHERE epochDay = :epochDay"
    )
    suspend fun addValidatedMinutes(epochDay: Long, minutes: Int, nowMs: Long)

    @Query(
        "UPDATE credit_day_state SET allowanceUsedMinutes = allowanceUsedMinutes + :minutes, " +
            "lastClockMs = :nowMs WHERE epochDay = :epochDay"
    )
    suspend fun addAllowanceUsed(epochDay: Long, minutes: Int, nowMs: Long)

    @Query("DELETE FROM credit_day_state WHERE epochDay < :keepFromEpochDay")
    suspend fun pruneBefore(keepFromEpochDay: Long)
}
