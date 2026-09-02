# Unlock Challenges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users earn wallet minutes through push-ups, a walk, or a mindful pause alongside hiragana study, and replace the study streak with a measured productivity level.

**Architecture:** Each challenge keeps its own screen and ViewModel, but all four funnel through a single `ChallengeRepository.award()` that applies rates, daily caps and the wallet ceiling, then logs a `challenge_completions` row. The productivity level is a pure function over seven days of locked-app usage, credits earned, and days the lock service was actually running.

**Tech Stack:** Kotlin, Jetpack Compose, Room (schema v4), Kotlin coroutines/Flow, Android `SensorManager` (`TYPE_PROXIMITY`, `TYPE_STEP_COUNTER`), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-02-unlock-challenges-design.md`

## Global Constraints

- `minSdk = 26`, `targetSdk = 37`. No SQLite `ON CONFLICT DO UPDATE` (needs API 30) — use insert-ignore then update.
- Wallet ceiling is `Wallet.MAX_BALANCE_MINUTES = 60`. Every award clamps to it.
- Restraint scale is **90** minutes, not 60. Effort target is **15** minutes. Weights are restraint `0.45`, effort `0.35`, consistency `0.20`.
- A day is **observed** when `protected_days.observedMinutes >= 15`.
- Band thresholds: Drifting `<20`, Settling `<45`, Steady `<65`, Focused `<85`, Locked in `>=85`.
- Rates: push-ups 10 reps → 3 min (cap 20/day); walk 300 steps → 6 min (cap 20/day); pause 60 s → 2 min (cap 10/day); study uses `CreditCalculator` and has **no** cap in the funnel.
- Unit tests run with `./gradlew :app:testDebugUnitTest`. Test style is JUnit 4, `org.junit.Assert.*`, backtick test names — match `app/src/test/java/com/example/jikan/study/CreditCalculatorTest.kt`.
- Every user-facing string states what happened and what to do. No apologies, no vague copy.
- Commit after every task.

---

## File Structure

**Create — pure logic (no Android types, JVM-testable):**
- `app/src/main/java/com/example/jikan/challenge/ProximityRepCounter.kt` — push-up rep state machine
- `app/src/main/java/com/example/jikan/challenge/CreditRules.kt` — rate and cap table
- `app/src/main/java/com/example/jikan/productivity/ProductivityScore.kt` — the formula

**Create — data:**
- `app/src/main/java/com/example/jikan/data/ChallengeType.kt`
- `app/src/main/java/com/example/jikan/data/ChallengeCompletion.kt`
- `app/src/main/java/com/example/jikan/data/ChallengeCompletionDao.kt`
- `app/src/main/java/com/example/jikan/data/ProtectedDay.kt`
- `app/src/main/java/com/example/jikan/data/ProtectedDayDao.kt`
- `app/src/main/java/com/example/jikan/challenge/ChallengeRepository.kt`
- `app/src/main/java/com/example/jikan/productivity/ProductivityRepository.kt`

**Create — UI:**
- `app/src/main/java/com/example/jikan/ui/challenge/ChallengePickerScreen.kt`
- `app/src/main/java/com/example/jikan/ui/challenge/PushUpScreen.kt`
- `app/src/main/java/com/example/jikan/ui/challenge/PushUpViewModel.kt`
- `app/src/main/java/com/example/jikan/ui/challenge/PauseScreen.kt`
- `app/src/main/java/com/example/jikan/ui/challenge/PauseViewModel.kt`
- `app/src/main/java/com/example/jikan/ui/challenge/StepsScreen.kt`
- `app/src/main/java/com/example/jikan/ui/challenge/StepsViewModel.kt`
- `app/src/main/java/com/example/jikan/ui/challenge/ChallengeAwardScreen.kt` — shared result screen

**Modify:**
- `data/AppDatabase.kt` (v4 + migration + 2 DAOs), `data/Converters.kt`, `data/Wallet.kt`, `data/StudyRepository.kt`
- `study/CreditCalculator.kt`, `study/StudySessionViewModel.kt`
- `service/AppLockAccessibilityService.kt`
- `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`
- `ui/lock/LockScreen.kt`, `ui/lock/LockActivity.kt`, `MainActivity.kt`
- `widget/JikanWidget.kt`, `app/src/main/AndroidManifest.xml`

---

### Task 1: Push-up rep counter

The plausibility rules from the spec live here. Pure, so they get tested without a phone.

**Files:**
- Create: `app/src/main/java/com/example/jikan/challenge/ProximityRepCounter.kt`
- Test: `app/src/test/java/com/example/jikan/challenge/ProximityRepCounterTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `class ProximityRepCounter(minRepMillis: Long = 800L, minNearMillis: Long = 250L, maxNearMillis: Long = 5_000L)` with `fun onReading(near: Boolean, timestampMillis: Long): Int`, `val reps: Int`, `fun reset()`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/jikan/challenge/ProximityRepCounterTest.kt`:

```kotlin
package com.example.jikan.challenge

import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityRepCounterTest {
    @Test
    fun `a full down-and-up cycle counts one rep`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        assertEquals(1, counter.onReading(near = false, timestampMillis = 1_000))
    }

    @Test
    fun `consecutive reps count when spaced far enough apart`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        counter.onReading(near = false, timestampMillis = 1_000)
        counter.onReading(near = true, timestampMillis = 1_500)
        assertEquals(2, counter.onReading(near = false, timestampMillis = 2_500))
    }

    @Test
    fun `a flick past the sensor is rejected`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        assertEquals(0, counter.onReading(near = false, timestampMillis = 100))
    }

    @Test
    fun `reps closer together than the minimum are rejected`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        counter.onReading(near = false, timestampMillis = 1_000)
        counter.onReading(near = true, timestampMillis = 1_100)
        assertEquals(1, counter.onReading(near = false, timestampMillis = 1_500))
    }

    @Test
    fun `a phone set down on the sensor is not a rep`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        assertEquals(0, counter.onReading(near = false, timestampMillis = 6_000))
    }

    @Test
    fun `repeated identical readings do not double count`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        counter.onReading(near = true, timestampMillis = 100)
        counter.onReading(near = false, timestampMillis = 1_000)
        assertEquals(1, counter.onReading(near = false, timestampMillis = 1_100))
    }

    @Test
    fun `a far reading before any near reading is ignored`() {
        val counter = ProximityRepCounter()
        assertEquals(0, counter.onReading(near = false, timestampMillis = 500))
    }

    @Test
    fun `out of order timestamps do not count a rep`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 5_000)
        assertEquals(0, counter.onReading(near = false, timestampMillis = 1_000))
    }

    @Test
    fun `reset clears progress`() {
        val counter = ProximityRepCounter()
        counter.onReading(near = true, timestampMillis = 0)
        counter.onReading(near = false, timestampMillis = 1_000)
        counter.reset()
        assertEquals(0, counter.reps)
        counter.onReading(near = true, timestampMillis = 2_000)
        assertEquals(1, counter.onReading(near = false, timestampMillis = 3_000))
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ProximityRepCounterTest*"`
Expected: FAIL — unresolved reference `ProximityRepCounter`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/jikan/challenge/ProximityRepCounter.kt`:

```kotlin
package com.example.jikan.challenge

/**
 * Counts push-ups from proximity readings.
 *
 * Deliberately free of Android types: timestamped booleans in, rep count out, so the
 * plausibility rules are tested on the JVM rather than by doing push-ups on the floor.
 *
 * A rep is FAR -> NEAR -> FAR, counted on the rising edge, and discarded when the near
 * phase is implausibly short (a hand flicked past the sensor), implausibly long (the
 * phone set face-down), or too soon after the previous rep.
 */
class ProximityRepCounter(
    private val minRepMillis: Long = 800L,
    private val minNearMillis: Long = 250L,
    private val maxNearMillis: Long = 5_000L,
) {
    var reps: Int = 0
        private set

    private var isNear = false
    private var nearSinceMillis: Long? = null
    private var lastRepAtMillis: Long? = null

    /** Feeds one reading in and returns the rep count after it. */
    fun onReading(near: Boolean, timestampMillis: Long): Int {
        if (near == isNear) return reps
        isNear = near

        if (near) {
            nearSinceMillis = timestampMillis
            return reps
        }

        val nearSince = nearSinceMillis ?: return reps
        nearSinceMillis = null

        val nearDuration = timestampMillis - nearSince
        if (nearDuration < minNearMillis || nearDuration > maxNearMillis) return reps

        val lastRep = lastRepAtMillis
        if (lastRep != null && timestampMillis - lastRep < minRepMillis) return reps

        reps++
        lastRepAtMillis = timestampMillis
        return reps
    }

    fun reset() {
        reps = 0
        isNear = false
        nearSinceMillis = null
        lastRepAtMillis = null
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ProximityRepCounterTest*"`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/jikan/challenge/ProximityRepCounter.kt app/src/test/java/com/example/jikan/challenge/ProximityRepCounterTest.kt
git commit -m "feat: add proximity rep counter with plausibility rules"
```

---

### Task 2: Challenge type and credit rules

**Files:**
- Create: `app/src/main/java/com/example/jikan/data/ChallengeType.kt`
- Create: `app/src/main/java/com/example/jikan/challenge/CreditRules.kt`
- Test: `app/src/test/java/com/example/jikan/challenge/CreditRulesTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum class ChallengeType { STUDY, PUSHUPS, PAUSE, STEPS }`; `CreditRules.earnedFor(type: ChallengeType, metric: Int): Int`; `CreditRules.dailyCapMinutes(type: ChallengeType): Int?` (null means uncapped in the funnel); constants `PUSHUP_TARGET_REPS = 10`, `STEPS_TARGET = 300`, `PAUSE_TARGET_SECONDS = 60`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/jikan/challenge/CreditRulesTest.kt`:

```kotlin
package com.example.jikan.challenge

import com.example.jikan.data.ChallengeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreditRulesTest {
    @Test
    fun `ten push-ups earn three minutes`() {
        assertEquals(3, CreditRules.earnedFor(ChallengeType.PUSHUPS, metric = 10))
    }

    @Test
    fun `extra push-ups do not earn extra minutes`() {
        assertEquals(3, CreditRules.earnedFor(ChallengeType.PUSHUPS, metric = 25))
    }

    @Test
    fun `an unfinished challenge earns nothing`() {
        assertEquals(0, CreditRules.earnedFor(ChallengeType.PUSHUPS, metric = 9))
        assertEquals(0, CreditRules.earnedFor(ChallengeType.STEPS, metric = 299))
        assertEquals(0, CreditRules.earnedFor(ChallengeType.PAUSE, metric = 59))
    }

    @Test
    fun `three hundred steps earn six minutes`() {
        assertEquals(6, CreditRules.earnedFor(ChallengeType.STEPS, metric = 300))
    }

    @Test
    fun `a sixty second pause earns two minutes`() {
        assertEquals(2, CreditRules.earnedFor(ChallengeType.PAUSE, metric = 60))
    }

    @Test
    fun `physical challenges are capped per day`() {
        assertEquals(20, CreditRules.dailyCapMinutes(ChallengeType.PUSHUPS))
        assertEquals(20, CreditRules.dailyCapMinutes(ChallengeType.STEPS))
        assertEquals(10, CreditRules.dailyCapMinutes(ChallengeType.PAUSE))
    }

    @Test
    fun `study is not capped in the funnel`() {
        assertNull(CreditRules.dailyCapMinutes(ChallengeType.STUDY))
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*CreditRulesTest*"`
Expected: FAIL — unresolved references `ChallengeType`, `CreditRules`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/jikan/data/ChallengeType.kt`:

```kotlin
package com.example.jikan.data

/** The ways a user can earn wallet minutes. All of them pay into the same wallet. */
enum class ChallengeType {
    STUDY,
    PUSHUPS,
    PAUSE,
    STEPS,
}
```

Create `app/src/main/java/com/example/jikan/challenge/CreditRules.kt`:

```kotlin
package com.example.jikan.challenge

import com.example.jikan.data.ChallengeType

/**
 * The whole rate table in one readable place.
 *
 * Rates are set by return — minutes earned per minute of real effort — so that no
 * challenge is obviously the only sensible choice: push-ups 6:1, study ~4:1, walking
 * and pausing 2:1. Push-ups keep a modest edge because physical effort per second is
 * genuinely higher than reading flashcards.
 */
object CreditRules {
    const val PUSHUP_TARGET_REPS = 10
    const val PUSHUP_MINUTES = 3
    const val PUSHUP_DAILY_CAP_MINUTES = 20

    const val STEPS_TARGET = 300
    const val STEPS_MINUTES = 6
    const val STEPS_DAILY_CAP_MINUTES = 20

    const val PAUSE_TARGET_SECONDS = 60
    const val PAUSE_MINUTES = 2
    const val PAUSE_DAILY_CAP_MINUTES = 10

    /** Minutes earned for completing [type] with [metric]. Study computes its own. */
    fun earnedFor(type: ChallengeType, metric: Int): Int = when (type) {
        ChallengeType.PUSHUPS -> if (metric >= PUSHUP_TARGET_REPS) PUSHUP_MINUTES else 0
        ChallengeType.STEPS -> if (metric >= STEPS_TARGET) STEPS_MINUTES else 0
        ChallengeType.PAUSE -> if (metric >= PAUSE_TARGET_SECONDS) PAUSE_MINUTES else 0
        ChallengeType.STUDY -> error("Study credits come from CreditCalculator, not CreditRules")
    }

    /** Null means no cap in the funnel; study is limited by CreditCalculator's decay. */
    fun dailyCapMinutes(type: ChallengeType): Int? = when (type) {
        ChallengeType.PUSHUPS -> PUSHUP_DAILY_CAP_MINUTES
        ChallengeType.STEPS -> STEPS_DAILY_CAP_MINUTES
        ChallengeType.PAUSE -> PAUSE_DAILY_CAP_MINUTES
        ChallengeType.STUDY -> null
    }

    fun targetFor(type: ChallengeType): Int = when (type) {
        ChallengeType.PUSHUPS -> PUSHUP_TARGET_REPS
        ChallengeType.STEPS -> STEPS_TARGET
        ChallengeType.PAUSE -> PAUSE_TARGET_SECONDS
        ChallengeType.STUDY -> 0
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*CreditRulesTest*"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/jikan/data/ChallengeType.kt app/src/main/java/com/example/jikan/challenge/CreditRules.kt app/src/test/java/com/example/jikan/challenge/CreditRulesTest.kt
git commit -m "feat: add challenge types and the credit rate table"
```

---

### Task 3: Productivity score

The worked examples from the spec become the test fixtures.

**Files:**
- Create: `app/src/main/java/com/example/jikan/productivity/ProductivityScore.kt`
- Test: `app/src/test/java/com/example/jikan/productivity/ProductivityScoreTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `data class DaySignal(lockedAppMinutes: Int, creditsEarned: Int, hadCompletion: Boolean, observed: Boolean)`; `enum class ProductivityBand { DRIFTING, SETTLING, STEADY, FOCUSED, LOCKED_IN }`; `data class ProductivityResult(score: Int, band: ProductivityBand, observedDays: Int, unobservedDays: Int)`; `ProductivityScore.compute(days: List<DaySignal>): ProductivityResult?` returning null when there is not enough history; `ProductivityScore.WINDOW_DAYS = 7`; `ProductivityScore.OBSERVED_THRESHOLD_MINUTES = 15`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/jikan/productivity/ProductivityScoreTest.kt`:

```kotlin
package com.example.jikan.productivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProductivityScoreTest {
    private fun week(
        locked: List<Int>,
        credits: List<Int>,
        activeDays: Int,
        observed: Boolean = true,
    ): List<DaySignal> = List(7) { index ->
        DaySignal(
            lockedAppMinutes = locked[index],
            creditsEarned = credits[index],
            hadCompletion = index < activeDays,
            observed = observed,
        )
    }

    @Test
    fun `a good week scores locked in`() {
        val result = ProductivityScore.compute(
            week(
                locked = listOf(10, 5, 0, 15, 8, 12, 6),
                credits = listOf(18, 12, 0, 20, 15, 10, 14),
                activeDays = 6,
            )
        )
        assertNotNull(result)
        assertEquals(85, result!!.score)
        assertEquals(ProductivityBand.LOCKED_IN, result.band)
    }

    @Test
    fun `a heavy week scores settling`() {
        val result = ProductivityScore.compute(
            week(
                locked = listOf(55, 60, 48, 60, 52, 58, 45),
                credits = listOf(8, 0, 0, 5, 0, 0, 6),
                activeDays = 3,
            )
        )
        assertEquals(33, result!!.score)
        assertEquals(ProductivityBand.SETTLING, result.band)
    }

    @Test
    fun `a bad week scores drifting`() {
        val result = ProductivityScore.compute(
            week(
                locked = listOf(120, 95, 140, 110, 90, 130, 100),
                credits = listOf(0, 5, 0, 0, 0, 0, 0),
                activeDays = 1,
            )
        )
        assertEquals(5, result!!.score)
        assertEquals(ProductivityBand.DRIFTING, result.band)
    }

    @Test
    fun `never touching a locked app scores steady even without studying`() {
        val result = ProductivityScore.compute(
            week(locked = List(7) { 0 }, credits = List(7) { 0 }, activeDays = 0)
        )
        assertEquals(45, result!!.score)
        assertEquals(ProductivityBand.STEADY, result.band)
    }

    @Test
    fun `no history at all yields no score`() {
        val days = List(7) {
            DaySignal(lockedAppMinutes = 0, creditsEarned = 0, hadCompletion = false, observed = false)
        }
        assertNull(ProductivityScore.compute(days))
    }

    @Test
    fun `unobserved days are excluded from restraint rather than counted as spotless`() {
        val days = List(7) { index ->
            DaySignal(
                lockedAppMinutes = if (index == 0) 90 else 0,
                creditsEarned = 0,
                hadCompletion = false,
                observed = index == 0,
            )
        }
        // Only the observed day counts, and it burned the whole scale, so restraint is 0.
        assertEquals(0, ProductivityScore.compute(days)!!.score)
    }

    @Test
    fun `with nothing observed the remaining weights are renormalised`() {
        val days = List(7) {
            DaySignal(lockedAppMinutes = 0, creditsEarned = 15, hadCompletion = true, observed = false)
        }
        // effort 1.0 and consistency 1.0, renormalised over 0.55, is a full score.
        assertEquals(100, ProductivityScore.compute(days)!!.score)
    }

    @Test
    fun `band boundaries fall on the documented scores`() {
        assertEquals(ProductivityBand.DRIFTING, ProductivityScore.bandFor(19))
        assertEquals(ProductivityBand.SETTLING, ProductivityScore.bandFor(20))
        assertEquals(ProductivityBand.SETTLING, ProductivityScore.bandFor(44))
        assertEquals(ProductivityBand.STEADY, ProductivityScore.bandFor(45))
        assertEquals(ProductivityBand.STEADY, ProductivityScore.bandFor(64))
        assertEquals(ProductivityBand.FOCUSED, ProductivityScore.bandFor(65))
        assertEquals(ProductivityBand.FOCUSED, ProductivityScore.bandFor(84))
        assertEquals(ProductivityBand.LOCKED_IN, ProductivityScore.bandFor(85))
    }

    @Test
    fun `it reports how many days went unobserved`() {
        val days = List(7) { index ->
            DaySignal(lockedAppMinutes = 0, creditsEarned = 0, hadCompletion = true, observed = index < 4)
        }
        val result = ProductivityScore.compute(days)!!
        assertEquals(4, result.observedDays)
        assertEquals(3, result.unobservedDays)
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ProductivityScoreTest*"`
Expected: FAIL — unresolved reference `ProductivityScore`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/jikan/productivity/ProductivityScore.kt`:

```kotlin
package com.example.jikan.productivity

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One day of evidence. [observed] is false when the lock service was not running. */
data class DaySignal(
    val lockedAppMinutes: Int,
    val creditsEarned: Int,
    val hadCompletion: Boolean,
    val observed: Boolean,
)

enum class ProductivityBand { DRIFTING, SETTLING, STEADY, FOCUSED, LOCKED_IN }

data class ProductivityResult(
    val score: Int,
    val band: ProductivityBand,
    val observedDays: Int,
    val unobservedDays: Int,
)

/**
 * A rolling seven-day productivity level, replacing the old all-or-nothing streak: one
 * missed day costs a seventh rather than everything.
 *
 * The restraint scale is 90 minutes rather than the 60-minute wallet ceiling on
 * purpose. Anchored at 60, a user who earned 60 minutes legitimately and spent them
 * would score zero on the heaviest-weighted term — perfect compliance producing the
 * worst possible restraint, with the score contradicting the economy underneath it.
 */
object ProductivityScore {
    const val WINDOW_DAYS = 7
    const val RESTRAINT_SCALE_MINUTES = 90
    const val EFFORT_TARGET_MINUTES = 15

    /** A day counts as observed once the service has been seen alive this long. */
    const val OBSERVED_THRESHOLD_MINUTES = 15

    private const val W_RESTRAINT = 0.45
    private const val W_EFFORT = 0.35
    private const val W_CONSISTENCY = 0.20

    /** Null when there is no history to judge — a fresh install is not a failure. */
    fun compute(days: List<DaySignal>): ProductivityResult? {
        if (days.isEmpty()) return null

        val observed = days.filter { it.observed }
        val activeDays = days.count { it.hadCompletion }
        if (observed.isEmpty() && activeDays == 0) return null

        val effort = days.sumOf {
            min(1.0, it.creditsEarned / EFFORT_TARGET_MINUTES.toDouble())
        } / days.size
        val consistency = activeDays.toDouble() / days.size

        val weighted = if (observed.isEmpty()) {
            // Nothing was watched, so restraint is unknowable. Renormalise rather than
            // inventing a value for it.
            val remaining = W_EFFORT + W_CONSISTENCY
            (W_EFFORT / remaining) * effort + (W_CONSISTENCY / remaining) * consistency
        } else {
            val restraint = observed.sumOf {
                max(0.0, 1.0 - it.lockedAppMinutes / RESTRAINT_SCALE_MINUTES.toDouble())
            } / observed.size
            W_RESTRAINT * restraint + W_EFFORT * effort + W_CONSISTENCY * consistency
        }

        val score = (weighted * 100).roundToInt().coerceIn(0, 100)
        return ProductivityResult(
            score = score,
            band = bandFor(score),
            observedDays = observed.size,
            unobservedDays = days.size - observed.size,
        )
    }

    fun bandFor(score: Int): ProductivityBand = when {
        score < 20 -> ProductivityBand.DRIFTING
        score < 45 -> ProductivityBand.SETTLING
        score < 65 -> ProductivityBand.STEADY
        score < 85 -> ProductivityBand.FOCUSED
        else -> ProductivityBand.LOCKED_IN
    }

    fun label(band: ProductivityBand): String = when (band) {
        ProductivityBand.DRIFTING -> "Drifting"
        ProductivityBand.SETTLING -> "Settling"
        ProductivityBand.STEADY -> "Steady"
        ProductivityBand.FOCUSED -> "Focused"
        ProductivityBand.LOCKED_IN -> "Locked in"
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ProductivityScoreTest*"`
Expected: PASS, 9 tests. If the good-week case reports 84 or 86, the weights or the
scale are wrong — do not adjust the expected value, fix the formula.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/jikan/productivity/ProductivityScore.kt app/src/test/java/com/example/jikan/productivity/ProductivityScoreTest.kt
git commit -m "feat: add the seven-day productivity score"
```

---

### Task 4: Schema v4 — challenge log, protected days, and dropping the streak

This is the riskiest task in the plan: it recreates the `wallet` table, and it fails by
destroying data. The verification steps are not optional.

**Files:**
- Create: `app/src/main/java/com/example/jikan/data/ChallengeCompletion.kt`
- Create: `app/src/main/java/com/example/jikan/data/ChallengeCompletionDao.kt`
- Create: `app/src/main/java/com/example/jikan/data/ProtectedDay.kt`
- Create: `app/src/main/java/com/example/jikan/data/ProtectedDayDao.kt`
- Modify: `app/src/main/java/com/example/jikan/data/Wallet.kt`
- Modify: `app/src/main/java/com/example/jikan/data/Converters.kt`
- Modify: `app/src/main/java/com/example/jikan/data/AppDatabase.kt`

**Interfaces:**
- Consumes: `ChallengeType` from Task 2.
- Produces: `ChallengeCompletion(id, type, epochDay, completedAt, metric, creditsEarned, creditsGranted)`; `ChallengeCompletionDao` with `insert`, `creditsGrantedToday(type, epochDay): Int`, `observeSince(epochDay): Flow<List<ChallengeCompletion>>`; `ProtectedDay(epochDay, observedMinutes)`; `ProtectedDayDao` with `insertIfAbsent`, `addMinutes`, `observeSince(epochDay): Flow<List<ProtectedDay>>`; `AppDatabase.challengeCompletionDao()`, `AppDatabase.protectedDayDao()`; `Wallet` without `currentStreakDays` or `lastStudyEpochDay`.

- [ ] **Step 1: Record the current on-device data so the migration can be proven**

```bash
adb exec-out run-as com.example.jikan cat databases/jikan.db > /tmp/before.db
sqlite3 /tmp/before.db "SELECT creditBalanceMinutes, lifetimeCreditsEarned FROM wallet;"
sqlite3 /tmp/before.db "SELECT COUNT(*) FROM user_card_progress;"
sqlite3 /tmp/before.db "SELECT COUNT(*) FROM locked_apps;"
sqlite3 /tmp/before.db "SELECT userName FROM user_settings;"
```

Write the four values down. They are the pass criteria in Step 7.

- [ ] **Step 2: Create the new entities and DAOs**

Create `app/src/main/java/com/example/jikan/data/ChallengeCompletion.kt`:

```kotlin
package com.example.jikan.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed challenge.
 *
 * [creditsEarned] is what the rate awarded before caps; [creditsGranted] is what
 * actually reached the wallet. They are stored separately so the productivity score can
 * measure effort — a user whose wallet happened to be full still did the work.
 */
@Entity(tableName = "challenge_completions", indices = [Index("epochDay")])
data class ChallengeCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ChallengeType,
    val epochDay: Long,
    val completedAt: Long,
    val metric: Int,
    val creditsEarned: Int,
    val creditsGranted: Int,
)
```

Create `app/src/main/java/com/example/jikan/data/ChallengeCompletionDao.kt`:

```kotlin
package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeCompletionDao {
    @Insert
    suspend fun insert(completion: ChallengeCompletion): Long

    @Query(
        "SELECT COALESCE(SUM(creditsGranted), 0) FROM challenge_completions " +
            "WHERE type = :type AND epochDay = :epochDay"
    )
    suspend fun creditsGrantedToday(type: ChallengeType, epochDay: Long): Int

    @Query("SELECT * FROM challenge_completions WHERE epochDay >= :epochDay")
    fun observeSince(epochDay: Long): Flow<List<ChallengeCompletion>>
}
```

Create `app/src/main/java/com/example/jikan/data/ProtectedDay.kt`:

```kotlin
package com.example.jikan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How many minutes the lock service was seen alive on a given day.
 *
 * This exists so the productivity score can tell "a quiet day" from "Jikan was not
 * running", which otherwise look identical: both record no locked-app usage.
 */
@Entity(tableName = "protected_days")
data class ProtectedDay(
    @PrimaryKey val epochDay: Long,
    val observedMinutes: Int,
)
```

Create `app/src/main/java/com/example/jikan/data/ProtectedDayDao.kt`:

```kotlin
package com.example.jikan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedDayDao {
    /** Paired with [addMinutes]; SQLite UPSERT needs API 30 and this app supports 26. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(day: ProtectedDay)

    @Query("UPDATE protected_days SET observedMinutes = observedMinutes + :minutes WHERE epochDay = :epochDay")
    suspend fun addMinutes(epochDay: Long, minutes: Int)

    @Query("SELECT * FROM protected_days WHERE epochDay >= :epochDay")
    fun observeSince(epochDay: Long): Flow<List<ProtectedDay>>
}
```

- [ ] **Step 3: Drop the streak fields from Wallet**

Edit `app/src/main/java/com/example/jikan/data/Wallet.kt` — remove `currentStreakDays`
and `lastStudyEpochDay` so the data class reads:

```kotlin
@Entity(tableName = "wallet")
data class Wallet(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val creditBalanceMinutes: Int = 0,
    val lifetimeCreditsEarned: Int = 0,
) {
    companion object {
        const val SINGLETON_ID = 1

        /** Spendable balance never exceeds this, whether earned or passively regenerated. */
        const val MAX_BALANCE_MINUTES = 60
    }
}
```

- [ ] **Step 4: Add the ChallengeType converter**

Append to the `Converters` class in `app/src/main/java/com/example/jikan/data/Converters.kt`:

```kotlin
    @TypeConverter
    fun fromChallengeType(type: ChallengeType): String = type.name

    @TypeConverter
    fun toChallengeType(value: String): ChallengeType = ChallengeType.valueOf(value)
```

- [ ] **Step 5: Register entities, bump to v4, and add the migration**

In `app/src/main/java/com/example/jikan/data/AppDatabase.kt`, add `ChallengeCompletion::class`
and `ProtectedDay::class` to the `entities` array, change `version = 3` to `version = 4`,
add the two abstract DAO accessors, add `MIGRATION_3_4` to the `addMigrations(...)` call,
and add this alongside the existing `MIGRATION_2_3`:

```kotlin
/**
 * Adds the challenge log and the protected-day log, and drops the streak columns from
 * wallet. SQLite cannot drop columns at this level, so wallet is recreated and copied.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `challenge_completions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, " +
                "`completedAt` INTEGER NOT NULL, " +
                "`metric` INTEGER NOT NULL, " +
                "`creditsEarned` INTEGER NOT NULL, " +
                "`creditsGranted` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_challenge_completions_epochDay` " +
                "ON `challenge_completions` (`epochDay`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `protected_days` (" +
                "`epochDay` INTEGER NOT NULL, " +
                "`observedMinutes` INTEGER NOT NULL, " +
                "PRIMARY KEY(`epochDay`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wallet_new` (" +
                "`id` INTEGER NOT NULL, " +
                "`creditBalanceMinutes` INTEGER NOT NULL, " +
                "`lifetimeCreditsEarned` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO `wallet_new` (`id`, `creditBalanceMinutes`, `lifetimeCreditsEarned`) " +
                "SELECT `id`, `creditBalanceMinutes`, `lifetimeCreditsEarned` FROM `wallet`"
        )
        db.execSQL("DROP TABLE `wallet`")
        db.execSQL("ALTER TABLE `wallet_new` RENAME TO `wallet`")
    }
}
```

- [ ] **Step 6: Build, then diff the migration SQL against what Room generated**

The build will fail on the streak references in `StudyRepository`, `CreditCalculator`,
`HomeViewModel`, `HomeScreen` and `JikanWidget`. That is expected and Tasks 5–6 and 12–13
fix it. Run the KSP step alone so the schema is generated regardless:

```bash
./gradlew :app:kspDebugKotlin
grep -n "challenge_completions\|protected_days\|CREATE TABLE IF NOT EXISTS \`wallet\`" \
  app/build/generated/ksp/debug/kotlin/com/example/jikan/data/AppDatabase_Impl.kt
```

Compare each generated `CREATE TABLE` and `CREATE INDEX` string against the migration
above, character for character. If any differs, **copy the generated string into the
migration** — do not hand-correct it. A mismatch throws
`IllegalStateException: Room cannot verify the data integrity` on the next launch.

- [ ] **Step 7: Prove the migration preserves data (after Task 6 makes the app compile)**

Defer this step until the end of Task 6, when the project builds again, then:

```bash
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c && adb shell am force-stop com.example.jikan
adb shell am start -n com.example.jikan/.MainActivity
sleep 4 && adb logcat -d -s "AndroidRuntime:E"
adb exec-out run-as com.example.jikan cat databases/jikan.db > /tmp/after.db
sqlite3 /tmp/after.db "PRAGMA user_version;"
sqlite3 /tmp/after.db "SELECT creditBalanceMinutes, lifetimeCreditsEarned FROM wallet;"
sqlite3 /tmp/after.db "SELECT COUNT(*) FROM user_card_progress;"
sqlite3 /tmp/after.db "SELECT COUNT(*) FROM locked_apps;"
sqlite3 /tmp/after.db "SELECT userName FROM user_settings;"
sqlite3 /tmp/after.db "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('challenge_completions','protected_days');"
```

Pass criteria: logcat empty, `user_version` is 4, both new tables listed, and all four
values from Step 1 unchanged. If wallet balance came back zero, the migration lost data —
`git checkout .` and fix the copy statement before going further.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/jikan/data/
git commit -m "feat: add schema v4 with challenge and protected-day logs"
```

---

### Task 5: The award funnel

**Files:**
- Create: `app/src/main/java/com/example/jikan/challenge/ChallengeRepository.kt`
- Test: `app/src/test/java/com/example/jikan/challenge/ChallengeRepositoryTest.kt`

**Interfaces:**
- Consumes: `CreditRules` (Task 2), `ChallengeCompletionDao`, `WalletDao`, `Wallet` (Task 4).
- Produces: `enum class CapReason { DAILY_CAP, WALLET_FULL }`; `data class ChallengeAward(type, metric, earned, granted, walletBalance, cappedBy)`; `class ChallengeRepository(walletDao, completionDao, zone)` with `suspend fun award(type: ChallengeType, metric: Int, earnedMinutes: Int, now: Long = System.currentTimeMillis()): ChallengeAward`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/jikan/challenge/ChallengeRepositoryTest.kt`:

```kotlin
package com.example.jikan.challenge

import com.example.jikan.data.ChallengeCompletion
import com.example.jikan.data.ChallengeCompletionDao
import com.example.jikan.data.ChallengeType
import com.example.jikan.data.Wallet
import com.example.jikan.data.WalletDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeWalletDao(initial: Wallet = Wallet()) : WalletDao {
    private val state = MutableStateFlow(initial)
    override suspend fun upsert(wallet: Wallet) { state.value = wallet }
    override suspend fun get(): Wallet = state.value
    override fun observe(): Flow<Wallet?> = state
    override suspend fun spend(minutes: Int) {
        state.value = state.value.copy(
            creditBalanceMinutes = maxOf(state.value.creditBalanceMinutes - minutes, 0)
        )
    }
    override suspend fun regenerate(minutes: Int) {
        state.value = state.value.copy(
            creditBalanceMinutes = minOf(
                state.value.creditBalanceMinutes + minutes,
                Wallet.MAX_BALANCE_MINUTES,
            )
        )
    }
    val balance: Int get() = state.value.creditBalanceMinutes
}

private class FakeCompletionDao : ChallengeCompletionDao {
    val rows = mutableListOf<ChallengeCompletion>()
    override suspend fun insert(completion: ChallengeCompletion): Long {
        rows += completion
        return rows.size.toLong()
    }
    override suspend fun creditsGrantedToday(type: ChallengeType, epochDay: Long): Int =
        rows.filter { it.type == type && it.epochDay == epochDay }.sumOf { it.creditsGranted }
    override fun observeSince(epochDay: Long): Flow<List<ChallengeCompletion>> =
        MutableStateFlow(rows.toList())
}

class ChallengeRepositoryTest {
    private fun repo(wallet: Wallet = Wallet()): Triple<ChallengeRepository, FakeWalletDao, FakeCompletionDao> {
        val walletDao = FakeWalletDao(wallet)
        val completionDao = FakeCompletionDao()
        return Triple(ChallengeRepository(walletDao, completionDao), walletDao, completionDao)
    }

    @Test
    fun `an uncapped award reaches the wallet in full`() = runBlocking {
        val (repository, walletDao, _) = repo()
        val award = repository.award(ChallengeType.PUSHUPS, metric = 10, earnedMinutes = 3)
        assertEquals(3, award.granted)
        assertEquals(3, walletDao.balance)
        assertNull(award.cappedBy)
    }

    @Test
    fun `the daily cap limits repeat awards`() = runBlocking {
        val (repository, _, _) = repo()
        repeat(6) { repository.award(ChallengeType.PUSHUPS, metric = 10, earnedMinutes = 3) }
        // Six awards of three is eighteen; the cap is twenty, so only two remain.
        val seventh = repository.award(ChallengeType.PUSHUPS, metric = 10, earnedMinutes = 3)
        assertEquals(2, seventh.granted)
        assertEquals(CapReason.DAILY_CAP, seventh.cappedBy)
    }

    @Test
    fun `the wallet ceiling limits an award`() = runBlocking {
        val (repository, walletDao, _) = repo(Wallet(creditBalanceMinutes = 58))
        val award = repository.award(ChallengeType.PUSHUPS, metric = 10, earnedMinutes = 3)
        assertEquals(2, award.granted)
        assertEquals(Wallet.MAX_BALANCE_MINUTES, walletDao.balance)
        assertEquals(CapReason.WALLET_FULL, award.cappedBy)
    }

    @Test
    fun `a full wallet grants nothing`() = runBlocking {
        val (repository, _, _) = repo(Wallet(creditBalanceMinutes = 60))
        val award = repository.award(ChallengeType.PAUSE, metric = 60, earnedMinutes = 2)
        assertEquals(0, award.granted)
        assertEquals(CapReason.WALLET_FULL, award.cappedBy)
    }

    @Test
    fun `study has no daily cap in the funnel`() = runBlocking {
        val (repository, _, _) = repo()
        repeat(3) { repository.award(ChallengeType.STUDY, metric = 10, earnedMinutes = 15) }
        // Three awards of fifteen would exceed any physical cap; only the wallet stops it.
        val fourth = repository.award(ChallengeType.STUDY, metric = 10, earnedMinutes = 15)
        assertEquals(CapReason.WALLET_FULL, fourth.cappedBy)
    }

    @Test
    fun `effort is logged before caps and payout after`() = runBlocking {
        val (repository, _, completionDao) = repo(Wallet(creditBalanceMinutes = 59))
        repository.award(ChallengeType.PUSHUPS, metric = 10, earnedMinutes = 3)
        assertEquals(3, completionDao.rows.single().creditsEarned)
        assertEquals(1, completionDao.rows.single().creditsGranted)
    }

    @Test
    fun `an award records the metric that produced it`() = runBlocking {
        val (repository, _, completionDao) = repo()
        repository.award(ChallengeType.STEPS, metric = 340, earnedMinutes = 6)
        assertEquals(340, completionDao.rows.single().metric)
        assertEquals(ChallengeType.STEPS, completionDao.rows.single().type)
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChallengeRepositoryTest*"`
Expected: FAIL — unresolved reference `ChallengeRepository`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/jikan/challenge/ChallengeRepository.kt`:

```kotlin
package com.example.jikan.challenge

import com.example.jikan.data.ChallengeCompletion
import com.example.jikan.data.ChallengeCompletionDao
import com.example.jikan.data.ChallengeType
import com.example.jikan.data.Wallet
import com.example.jikan.data.WalletDao
import java.time.Instant
import java.time.ZoneId

enum class CapReason { DAILY_CAP, WALLET_FULL }

data class ChallengeAward(
    val type: ChallengeType,
    val metric: Int,
    val earned: Int,
    val granted: Int,
    val walletBalance: Int,
    val cappedBy: CapReason?,
)

/**
 * The one place credits are minted.
 *
 * Every challenge — a lesson, push-ups, a walk, a pause — ends here, so the daily cap,
 * the wallet ceiling and the completion log all have a single implementation.
 */
class ChallengeRepository(
    private val walletDao: WalletDao,
    private val completionDao: ChallengeCompletionDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun award(
        type: ChallengeType,
        metric: Int,
        earnedMinutes: Int,
        now: Long = System.currentTimeMillis(),
    ): ChallengeAward {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        val wallet = walletDao.get() ?: Wallet()

        val cap = CreditRules.dailyCapMinutes(type)
        val afterCap = if (cap == null) {
            earnedMinutes
        } else {
            val already = completionDao.creditsGrantedToday(type, today)
            minOf(earnedMinutes, (cap - already).coerceAtLeast(0))
        }

        val walletRoom = (Wallet.MAX_BALANCE_MINUTES - wallet.creditBalanceMinutes).coerceAtLeast(0)
        val granted = minOf(afterCap, walletRoom).coerceAtLeast(0)

        val cappedBy = when {
            granted >= earnedMinutes -> null
            walletRoom <= afterCap -> CapReason.WALLET_FULL
            else -> CapReason.DAILY_CAP
        }

        val updated = wallet.copy(
            creditBalanceMinutes = wallet.creditBalanceMinutes + granted,
            lifetimeCreditsEarned = wallet.lifetimeCreditsEarned + granted,
        )
        walletDao.upsert(updated)

        completionDao.insert(
            ChallengeCompletion(
                type = type,
                epochDay = today,
                completedAt = now,
                metric = metric,
                creditsEarned = earnedMinutes,
                creditsGranted = granted,
            )
        )

        return ChallengeAward(
            type = type,
            metric = metric,
            earned = earnedMinutes,
            granted = granted,
            walletBalance = updated.creditBalanceMinutes,
            cappedBy = cappedBy,
        )
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChallengeRepositoryTest*"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/jikan/challenge/ChallengeRepository.kt app/src/test/java/com/example/jikan/challenge/ChallengeRepositoryTest.kt
git commit -m "feat: add the challenge award funnel with caps and ceiling"
```

---

### Task 6: Route study through the funnel and retire the streak bonus

Restores a compiling build. `CreditCalculator`'s streak bonus becomes a consistency
bonus driven by the same seven-day active-day count the productivity score uses.

**Files:**
- Modify: `app/src/main/java/com/example/jikan/study/CreditCalculator.kt`
- Modify: `app/src/test/java/com/example/jikan/study/CreditCalculatorTest.kt`
- Modify: `app/src/main/java/com/example/jikan/data/StudyRepository.kt`
- Modify: `app/src/main/java/com/example/jikan/study/StudySessionViewModel.kt`

**Interfaces:**
- Consumes: `ChallengeRepository.award` (Task 5), `ChallengeCompletionDao` (Task 4).
- Produces: `CreditCalculator.creditsEarned(correctCount, totalCount, activeDaysInWindow = 0, priorSessionsToday = 0)`; `SessionCompletionResult(creditsEarned, walletBalance, isPerfect, cappedBy)` — note `streakDays` is gone and `cappedBy: CapReason?` is added; `StudyRepository(cardDao, progressDao, sessionDao, walletDao, completionDao, challengeRepository)`.

- [ ] **Step 1: Update the CreditCalculator test to the new parameter**

In `app/src/test/java/com/example/jikan/study/CreditCalculatorTest.kt`, replace every
`currentStreakDays = N` argument with `activeDaysInWindow = N`, and rename the two streak
tests:

```kotlin
    @Test
    fun `a single active day does not yet earn the consistency bonus`() {
        val noBonus = CreditCalculator.creditsEarned(
            correctCount = 6,
            totalCount = 10,
            activeDaysInWindow = 1,
        )
        assertEquals(12, noBonus)
    }

    @Test
    fun `regular days earn the consistency bonus`() {
        val withBonus = CreditCalculator.creditsEarned(
            correctCount = 6,
            totalCount = 10,
            activeDaysInWindow = 2,
        )
        assertTrue(withBonus > 12)
    }
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*CreditCalculatorTest*"`
Expected: FAIL — no parameter named `activeDaysInWindow`.

- [ ] **Step 3: Rename the bonus in CreditCalculator**

In `app/src/main/java/com/example/jikan/study/CreditCalculator.kt`, rename
`STREAK_BONUS_MIN_DAYS` to `CONSISTENCY_BONUS_MIN_DAYS`, `STREAK_BONUS_RATE` to
`CONSISTENCY_BONUS_RATE`, the parameter `currentStreakDays` to `activeDaysInWindow`, and
the local `streakMultiplier` to `consistencyMultiplier`. Replace the KDoc line for the
parameter with:

```kotlin
     * @param activeDaysInWindow days in the trailing week with at least one completed
     *   challenge. Replaces the old streak: regularity still pays, but one missed day
     *   no longer wipes the bonus out.
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*CreditCalculatorTest*"`
Expected: PASS.

- [ ] **Step 5: Rewrite completeSession to use the funnel**

In `app/src/main/java/com/example/jikan/data/StudyRepository.kt`: delete `streakDays`
from `SessionCompletionResult` and add `val cappedBy: CapReason?`; add
`private val completionDao: ChallengeCompletionDao` and
`private val challengeRepository: ChallengeRepository` constructor parameters; then
replace the body of `completeSession` with:

```kotlin
    suspend fun completeSession(
        correctCount: Int,
        totalCount: Int,
        sessionStartedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): SessionCompletionResult {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        val startOfToday = LocalDate.ofEpochDay(today).atStartOfDay(zone).toInstant().toEpochMilli()

        val priorSessionsToday = sessionDao.getSince(startOfToday).size
        val windowStart = today - (ProductivityScore.WINDOW_DAYS - 1)
        val activeDaysInWindow = completionDao.observeSince(windowStart).first()
            .map { it.epochDay }
            .distinct()
            .size

        val creditsEarned = CreditCalculator.creditsEarned(
            correctCount = correctCount,
            totalCount = totalCount,
            activeDaysInWindow = activeDaysInWindow,
            priorSessionsToday = priorSessionsToday,
        )

        val award = challengeRepository.award(
            type = ChallengeType.STUDY,
            metric = correctCount,
            earnedMinutes = creditsEarned,
            now = now,
        )

        sessionDao.insert(
            Session(
                startedAt = sessionStartedAt,
                completedAt = now,
                questionsTotal = totalCount,
                questionsCorrect = correctCount,
                creditsEarned = creditsEarned,
                isPerfect = correctCount == totalCount,
            )
        )

        return SessionCompletionResult(
            creditsEarned = award.granted,
            walletBalance = award.walletBalance,
            isPerfect = correctCount == totalCount,
            cappedBy = award.cappedBy,
        )
    }
```

Add imports: `com.example.jikan.challenge.CapReason`, `com.example.jikan.challenge.ChallengeRepository`,
`com.example.jikan.productivity.ProductivityScore`, `kotlinx.coroutines.flow.first`.
Remove the now-unused `Wallet` import if the compiler flags it.

- [ ] **Step 6: Update the ViewModel's construction of StudyRepository**

In `app/src/main/java/com/example/jikan/study/StudySessionViewModel.kt`, replace the
`repository` field:

```kotlin
    private val repository = StudyRepository(
        cardDao = db.cardDao(),
        progressDao = db.progressDao(),
        sessionDao = db.sessionDao(),
        walletDao = db.walletDao(),
        completionDao = db.challengeCompletionDao(),
        challengeRepository = ChallengeRepository(db.walletDao(), db.challengeCompletionDao()),
    )
```

Add `import com.example.jikan.challenge.ChallengeRepository`.

- [ ] **Step 7: Build, then run Task 4 Step 7**

```bash
./gradlew :app:assembleDebug
```

The build should now succeed except for the streak references in `HomeViewModel`,
`HomeScreen` and `JikanWidget`. Fix those minimally to unblock: in `HomeUiState` delete
`streakDays`, in `HomeViewModel` delete the `streakDays = ...` assignment, in `HomeScreen`
replace the streak `LedgerStat` with `value = "—", unit = "", label = "LEVEL"` as a
placeholder Task 15 replaces, and in `JikanWidget` replace the streak `StatColumn` with
`value = "—"`. Then build again and **complete Task 4 Step 7 now** — the migration must be
proven before more code lands on top of it.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: route study credits through the award funnel, retire the streak"
```

---

### Task 7: Record the days Jikan was running

**Files:**
- Modify: `app/src/main/java/com/example/jikan/service/AppLockAccessibilityService.kt`

**Interfaces:**
- Consumes: `ProtectedDayDao` (Task 4).
- Produces: a `protected_days` row per day, incremented once per 60-second tick.

- [ ] **Step 1: Add the DAO field**

Alongside `private lateinit var appUsageDao: AppUsageDao`, add:

```kotlin
    private lateinit var protectedDayDao: ProtectedDayDao
```

and in `onServiceConnected`, after `appUsageDao = db.appUsageDao()`:

```kotlin
        protectedDayDao = db.protectedDayDao()
```

Add `import com.example.jikan.data.ProtectedDay` and `import com.example.jikan.data.ProtectedDayDao`.

- [ ] **Step 2: Record every tick, not only the spending ones**

Replace the body of `spendRunnable.run()` with:

```kotlin
        override fun run() {
            val foreground = currentForegroundPackage
            serviceScope.launch {
                recordProtectedMinute()
                if (foreground != null && foreground in lockedPackages && walletBalance > 0) {
                    walletDao.spend(SPEND_MINUTES_PER_TICK)
                    recordUsage(foreground, SPEND_MINUTES_PER_TICK)
                    WidgetUpdater.refresh(applicationContext)
                }
            }
            handler.postDelayed(this, SPEND_TICK_INTERVAL_MS)
        }
```

The recording is unconditional on purpose: it measures that Jikan was alive, not that the
user was spending.

- [ ] **Step 3: Add the recorder**

Next to `recordUsage`:

```kotlin
    /**
     * Marks this minute as observed, so the productivity score can tell a genuinely
     * quiet day from a day when the accessibility service was switched off.
     */
    private suspend fun recordProtectedMinute() {
        val today = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        protectedDayDao.insertIfAbsent(ProtectedDay(epochDay = today, observedMinutes = 0))
        protectedDayDao.addMinutes(epochDay = today, minutes = 1)
    }
```

- [ ] **Step 4: Build, install, and verify rows appear**

```bash
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Re-enable Jikan under Settings → Accessibility (installing revokes it on MIUI), wait two
minutes, then:

```bash
adb exec-out run-as com.example.jikan cat databases/jikan.db > /tmp/check.db
sqlite3 /tmp/check.db "SELECT * FROM protected_days;"
```

Expected: one row for today with `observedMinutes` of at least 1, growing on re-check.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/jikan/service/AppLockAccessibilityService.kt
git commit -m "feat: record the days the lock service was running"
```

---

### Task 8: Productivity repository

Turns stored rows into the seven `DaySignal`s the pure formula consumes.

**Files:**
- Create: `app/src/main/java/com/example/jikan/productivity/ProductivityRepository.kt`
- Modify: `app/src/main/java/com/example/jikan/data/AppUsageDao.kt`

**Interfaces:**
- Consumes: `AppUsageDao`, `ChallengeCompletionDao`, `ProtectedDayDao` (Task 4), `ProductivityScore` (Task 3).
- Produces: `AppUsageDao.observeSince(epochDay): Flow<List<AppUsage>>`; `class ProductivityRepository(appUsageDao, completionDao, protectedDayDao, zone)` with `fun observe(): Flow<ProductivityResult?>`.

- [ ] **Step 1: Add the windowed usage query**

Append to `ChallengeCompletionDao`'s sibling `app/src/main/java/com/example/jikan/data/AppUsageDao.kt`:

```kotlin
    @Query("SELECT * FROM app_usage WHERE epochDay >= :epochDay")
    fun observeSince(epochDay: Long): Flow<List<AppUsage>>
```

- [ ] **Step 2: Write the repository**

Create `app/src/main/java/com/example/jikan/productivity/ProductivityRepository.kt`:

```kotlin
package com.example.jikan.productivity

import com.example.jikan.data.AppUsageDao
import com.example.jikan.data.ChallengeCompletionDao
import com.example.jikan.data.ProtectedDayDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId

/**
 * Assembles the trailing week of evidence the productivity formula needs. The formula
 * itself stays pure and unit-tested; this class only does the gathering.
 */
class ProductivityRepository(
    private val appUsageDao: AppUsageDao,
    private val completionDao: ChallengeCompletionDao,
    private val protectedDayDao: ProtectedDayDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun observe(now: Long = System.currentTimeMillis()): Flow<ProductivityResult?> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        val windowStart = today - (ProductivityScore.WINDOW_DAYS - 1)

        return combine(
            appUsageDao.observeSince(windowStart),
            completionDao.observeSince(windowStart),
            protectedDayDao.observeSince(windowStart),
        ) { usage, completions, protectedDays ->
            val minutesByDay = usage.groupBy { it.epochDay }
                .mapValues { (_, rows) -> rows.sumOf { it.minutes } }
            val creditsByDay = completions.groupBy { it.epochDay }
                .mapValues { (_, rows) -> rows.sumOf { it.creditsEarned } }
            val activeDays = completions.map { it.epochDay }.toSet()
            val observedByDay = protectedDays.associate { it.epochDay to it.observedMinutes }

            val days = (0 until ProductivityScore.WINDOW_DAYS).map { offset ->
                val day = windowStart + offset
                DaySignal(
                    lockedAppMinutes = minutesByDay[day] ?: 0,
                    creditsEarned = creditsByDay[day] ?: 0,
                    hadCompletion = day in activeDays,
                    observed = (observedByDay[day] ?: 0) >= ProductivityScore.OBSERVED_THRESHOLD_MINUTES,
                )
            }
            ProductivityScore.compute(days)
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/jikan/productivity/ProductivityRepository.kt app/src/main/java/com/example/jikan/data/AppUsageDao.kt
git commit -m "feat: assemble the productivity window from stored rows"
```

---

### Task 9: Shared award screen

Every challenge ends here, including when it earned less than the user worked for.

**Files:**
- Create: `app/src/main/java/com/example/jikan/ui/challenge/ChallengeAwardScreen.kt`

**Interfaces:**
- Consumes: `ChallengeAward`, `CapReason` (Task 5), `NeoCard`/`PillButton` from `ui/theme/JikanComponents.kt`.
- Produces: `@Composable fun ChallengeAwardScreen(award: ChallengeAward, onDone: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the screen**

Create `app/src/main/java/com/example/jikan/ui/challenge/ChallengeAwardScreen.kt`:

```kotlin
package com.example.jikan.ui.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jikan.challenge.CapReason
import com.example.jikan.challenge.ChallengeAward
import com.example.jikan.ui.theme.NeoCard
import com.example.jikan.ui.theme.PillButton

@Composable
fun ChallengeAwardScreen(
    award: ChallengeAward,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NeoCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "+${award.granted} min",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Wallet: ${award.walletBalance} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Earning less than you worked for reads as a bug unless the screen says why.
        val explanation = when (award.cappedBy) {
            CapReason.DAILY_CAP ->
                "That's all this challenge pays today. Try another one, or come back tomorrow."
            CapReason.WALLET_FULL ->
                "Your wallet is full at 60 minutes. Spend some before earning more."
            null -> null
        }
        if (explanation != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(40.dp))
        PillButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/jikan/ui/challenge/ChallengeAwardScreen.kt
git commit -m "feat: add the shared challenge award screen"
```

---

### Task 10: Push-up challenge

**Files:**
- Create: `app/src/main/java/com/example/jikan/ui/challenge/PushUpViewModel.kt`
- Create: `app/src/main/java/com/example/jikan/ui/challenge/PushUpScreen.kt`

**Interfaces:**
- Consumes: `ProximityRepCounter` (Task 1), `CreditRules` (Task 2), `ChallengeRepository` (Task 5), `ChallengeAwardScreen` (Task 9), `WidgetUpdater`.
- Produces: `sealed interface ChallengeUiState` with `data class Unavailable(val reason: String)`, `data class Running(val progress: Int, val target: Int)`, `data class Done(val award: ChallengeAward)` — **shared by Tasks 10, 11 and 12**, declared in `PushUpViewModel.kt`; `class PushUpViewModel(application) : AndroidViewModel` with `val state: StateFlow<ChallengeUiState>`, `fun start()`, `fun stop()`; `@Composable fun PushUpScreen(onDone: () -> Unit, modifier: Modifier)`; `@Composable internal fun ChallengeUnavailable(reason: String, onBack: () -> Unit, modifier: Modifier)` in `PushUpScreen.kt`, reused by Tasks 11 and 12.

- [ ] **Step 1: Write the ViewModel**

Create `app/src/main/java/com/example/jikan/ui/challenge/PushUpViewModel.kt`:

```kotlin
package com.example.jikan.ui.challenge

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.challenge.ChallengeAward
import com.example.jikan.challenge.ChallengeRepository
import com.example.jikan.challenge.CreditRules
import com.example.jikan.challenge.ProximityRepCounter
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.ChallengeType
import com.example.jikan.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared by every challenge screen so they can share one award flow. */
sealed interface ChallengeUiState {
    data class Unavailable(val reason: String) : ChallengeUiState
    data class Running(val progress: Int, val target: Int) : ChallengeUiState
    data class Done(val award: ChallengeAward) : ChallengeUiState
}

class PushUpViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repository = ChallengeRepository(db.walletDao(), db.challengeCompletionDao())
    private val sensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximity: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val counter = ProximityRepCounter()

    private val _state = MutableStateFlow<ChallengeUiState>(
        if (proximity == null) {
            ChallengeUiState.Unavailable(
                "This phone has no proximity sensor, so push-ups can't be counted."
            )
        } else {
            ChallengeUiState.Running(progress = 0, target = CreditRules.PUSHUP_TARGET_REPS)
        }
    )
    val state: StateFlow<ChallengeUiState> = _state.asStateFlow()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val sensor = proximity ?: return
            // Most devices report binary near/far; some report centimetres.
            val isNear = event.values.firstOrNull()?.let { it < sensor.maximumRange } ?: return
            val reps = counter.onReading(isNear, System.currentTimeMillis())
            if (_state.value !is ChallengeUiState.Running) return
            if (reps >= CreditRules.PUSHUP_TARGET_REPS) {
                stop()
                finish(reps)
            } else {
                _state.value = ChallengeUiState.Running(reps, CreditRules.PUSHUP_TARGET_REPS)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val sensor = proximity ?: return
        counter.reset()
        _state.value = ChallengeUiState.Running(0, CreditRules.PUSHUP_TARGET_REPS)
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }

    private fun finish(reps: Int) {
        viewModelScope.launch {
            val award = repository.award(
                type = ChallengeType.PUSHUPS,
                metric = reps,
                earnedMinutes = CreditRules.earnedFor(ChallengeType.PUSHUPS, reps),
            )
            WidgetUpdater.refresh(getApplication())
            _state.value = ChallengeUiState.Done(award)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
```

- [ ] **Step 2: Write the screen**

Create `app/src/main/java/com/example/jikan/ui/challenge/PushUpScreen.kt`:

```kotlin
package com.example.jikan.ui.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.ui.theme.OutlinePillButton

@Composable
fun PushUpScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PushUpViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    when (val current = state) {
        is ChallengeUiState.Unavailable -> ChallengeUnavailable(current.reason, onDone, modifier)

        is ChallengeUiState.Running -> Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${current.progress}",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = "OF ${current.target} PUSH-UPS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Put the phone on the floor below your chest. Each rep counts when you come back up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            OutlinePillButton(text = "Cancel", onClick = onDone, modifier = Modifier.fillMaxWidth())
        }

        is ChallengeUiState.Done -> ChallengeAwardScreen(current.award, onDone, modifier)
    }
}

@Composable
internal fun ChallengeUnavailable(reason: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        OutlinePillButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/jikan/ui/challenge/PushUpViewModel.kt app/src/main/java/com/example/jikan/ui/challenge/PushUpScreen.kt
git commit -m "feat: add the push-up challenge"
```

---

### Task 11: Mindful pause challenge

**Files:**
- Create: `app/src/main/java/com/example/jikan/ui/challenge/PauseViewModel.kt`
- Create: `app/src/main/java/com/example/jikan/ui/challenge/PauseScreen.kt`

**Interfaces:**
- Consumes: `ChallengeUiState` (Task 10), `CreditRules`, `ChallengeRepository`, `ChallengeAwardScreen`.
- Produces: `class PauseViewModel(application)` with `val state: StateFlow<ChallengeUiState>`, `fun start()`, `fun reset()`; `@Composable fun PauseScreen(onDone: () -> Unit, modifier: Modifier)`.

- [ ] **Step 1: Write the ViewModel**

Create `app/src/main/java/com/example/jikan/ui/challenge/PauseViewModel.kt`:

```kotlin
package com.example.jikan.ui.challenge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.challenge.ChallengeRepository
import com.example.jikan.challenge.CreditRules
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.ChallengeType
import com.example.jikan.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PauseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repository = ChallengeRepository(db.walletDao(), db.challengeCompletionDao())

    private val _state = MutableStateFlow<ChallengeUiState>(
        ChallengeUiState.Running(progress = 0, target = CreditRules.PAUSE_TARGET_SECONDS)
    )
    val state: StateFlow<ChallengeUiState> = _state.asStateFlow()

    private var timer: Job? = null

    fun start() {
        if (timer?.isActive == true) return
        timer = viewModelScope.launch {
            var elapsed = 0
            while (elapsed < CreditRules.PAUSE_TARGET_SECONDS) {
                delay(1_000)
                elapsed++
                _state.value = ChallengeUiState.Running(elapsed, CreditRules.PAUSE_TARGET_SECONDS)
            }
            finish(elapsed)
        }
    }

    /**
     * Leaving the app restarts the pause. That is the mechanic, not a punishment:
     * without it the countdown could be backgrounded and farmed.
     */
    fun reset() {
        timer?.cancel()
        timer = null
        if (_state.value is ChallengeUiState.Done) return
        _state.value = ChallengeUiState.Running(0, CreditRules.PAUSE_TARGET_SECONDS)
    }

    private suspend fun finish(seconds: Int) {
        val award = repository.award(
            type = ChallengeType.PAUSE,
            metric = seconds,
            earnedMinutes = CreditRules.earnedFor(ChallengeType.PAUSE, seconds),
        )
        WidgetUpdater.refresh(getApplication())
        _state.value = ChallengeUiState.Done(award)
    }
}
```

- [ ] **Step 2: Write the screen**

Create `app/src/main/java/com/example/jikan/ui/challenge/PauseScreen.kt`:

```kotlin
package com.example.jikan.ui.challenge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.ui.theme.OutlinePillButton

@Composable
fun PauseScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PauseViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.start() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.reset() }

    when (val current = state) {
        is ChallengeUiState.Unavailable -> ChallengeUnavailable(current.reason, onDone, modifier)

        is ChallengeUiState.Running -> {
            val remaining = current.target - current.progress
            val breathe by animateFloatAsState(
                targetValue = if (current.progress % 2 == 0) 0.85f else 1f,
                animationSpec = tween(durationMillis = 1_000),
                label = "breathe",
            )
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .scale(breathe)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    )
                    Text(text = "$remaining", style = MaterialTheme.typography.displayLarge)
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Breathe with the circle. Leaving the app starts the pause over.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))
                OutlinePillButton(text = "Cancel", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
        }

        is ChallengeUiState.Done -> ChallengeAwardScreen(current.award, onDone, modifier)
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/jikan/ui/challenge/PauseViewModel.kt app/src/main/java/com/example/jikan/ui/challenge/PauseScreen.kt
git commit -m "feat: add the mindful pause challenge"
```

---

### Task 12: Walk challenge

**Files:**
- Create: `app/src/main/java/com/example/jikan/ui/challenge/StepsViewModel.kt`
- Create: `app/src/main/java/com/example/jikan/ui/challenge/StepsScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ChallengeUiState` (Task 10), `CreditRules`, `ChallengeRepository`, `ChallengeAwardScreen`, `ChallengeUnavailable`.
- Produces: `class StepsViewModel(application)` with `val state: StateFlow<ChallengeUiState>`, `fun start()`, `fun stop()`; `@Composable fun StepsScreen(onDone: () -> Unit, modifier: Modifier)`.

- [ ] **Step 1: Declare the permission**

In `app/src/main/AndroidManifest.xml`, directly above `<queries>`:

```xml
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
```

- [ ] **Step 2: Write the ViewModel**

Create `app/src/main/java/com/example/jikan/ui/challenge/StepsViewModel.kt`:

```kotlin
package com.example.jikan.ui.challenge

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.challenge.ChallengeRepository
import com.example.jikan.challenge.CreditRules
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.ChallengeType
import com.example.jikan.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StepsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repository = ChallengeRepository(db.walletDao(), db.challengeCompletionDao())
    private val sensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounter: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // Baseline is ephemeral in-progress state, not domain history, so it stays out of Room.
    private val prefs = application.getSharedPreferences("step_challenge", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<ChallengeUiState>(
        if (stepCounter == null) {
            ChallengeUiState.Unavailable(
                "This phone has no step counter, so walks can't be measured."
            )
        } else {
            ChallengeUiState.Running(progress = 0, target = CreditRules.STEPS_TARGET)
        }
    )
    val state: StateFlow<ChallengeUiState> = _state.asStateFlow()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val total = event.values.firstOrNull()?.toLong() ?: return
            val baseline = prefs.getLong(KEY_BASELINE, -1L)
            if (baseline < 0) {
                prefs.edit().putLong(KEY_BASELINE, total).apply()
                return
            }
            if (total < baseline) {
                // TYPE_STEP_COUNTER resets to zero on reboot, so the delta would go negative.
                prefs.edit().putLong(KEY_BASELINE, total).apply()
                _state.value = ChallengeUiState.Running(0, CreditRules.STEPS_TARGET)
                return
            }
            val walked = (total - baseline).toInt()
            if (_state.value !is ChallengeUiState.Running) return
            if (walked >= CreditRules.STEPS_TARGET) {
                stop()
                finish(walked)
            } else {
                _state.value = ChallengeUiState.Running(walked, CreditRules.STEPS_TARGET)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val sensor = stepCounter ?: return
        prefs.edit().remove(KEY_BASELINE).apply()
        _state.value = ChallengeUiState.Running(0, CreditRules.STEPS_TARGET)
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }

    fun onPermissionDenied() {
        _state.value = ChallengeUiState.Unavailable(
            "Jikan needs activity permission to count steps."
        )
    }

    private fun finish(steps: Int) {
        viewModelScope.launch {
            val award = repository.award(
                type = ChallengeType.STEPS,
                metric = steps,
                earnedMinutes = CreditRules.earnedFor(ChallengeType.STEPS, steps),
            )
            prefs.edit().remove(KEY_BASELINE).apply()
            WidgetUpdater.refresh(getApplication())
            _state.value = ChallengeUiState.Done(award)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    private companion object {
        const val KEY_BASELINE = "baseline"
    }
}
```

- [ ] **Step 3: Write the screen with the permission request**

Create `app/src/main/java/com/example/jikan/ui/challenge/StepsScreen.kt`:

```kotlin
package com.example.jikan.ui.challenge

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.ui.theme.JikanProgressBar
import com.example.jikan.ui.theme.OutlinePillButton

@Composable
fun StepsScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StepsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.start() else viewModel.onPermissionDenied() }

    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            viewModel.start()
        }
        onDispose { viewModel.stop() }
    }

    when (val current = state) {
        is ChallengeUiState.Unavailable -> ChallengeUnavailable(current.reason, onDone, modifier)

        is ChallengeUiState.Running -> Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${current.progress}",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = "OF ${current.target} STEPS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            JikanProgressBar(progress = current.progress / current.target.toFloat())
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Pocket the phone and walk. Steps keep counting with the screen off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            OutlinePillButton(text = "Cancel", onClick = onDone, modifier = Modifier.fillMaxWidth())
        }

        is ChallengeUiState.Done -> ChallengeAwardScreen(current.award, onDone, modifier)
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/jikan/ui/challenge/StepsViewModel.kt app/src/main/java/com/example/jikan/ui/challenge/StepsScreen.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add the walk challenge"
```

---

### Task 13: Challenge picker with live availability

The spec requires cap and wallet-full states to appear **before** a challenge starts —
letting someone do twenty push-ups for zero minutes is the exact failure this prevents.
So the picker needs its own read of today's totals, not a static list.

**Files:**
- Create: `app/src/main/java/com/example/jikan/ui/challenge/ChallengePickerViewModel.kt`
- Create: `app/src/main/java/com/example/jikan/ui/challenge/ChallengePickerScreen.kt`

**Interfaces:**
- Consumes: `ChallengeType` (Task 2), `CreditRules` (Task 2), `ChallengeCompletionDao` (Task 4), `WalletDao`, `NeoCard`/`IconChip`.
- Produces: `data class ChallengeAvailability(type: ChallengeType, blockedReason: String?)`; `class ChallengePickerViewModel(application)` with `val state: StateFlow<List<ChallengeAvailability>>` and `fun refresh()`; `@Composable fun ChallengePickerScreen(onPick: (ChallengeType) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier, title: String = "Earn time")`.

- [ ] **Step 1: Write the availability ViewModel**

Create `app/src/main/java/com/example/jikan/ui/challenge/ChallengePickerViewModel.kt`:

```kotlin
package com.example.jikan.ui.challenge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jikan.challenge.CreditRules
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.ChallengeType
import com.example.jikan.data.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** [blockedReason] is null when the challenge is worth starting right now. */
data class ChallengeAvailability(
    val type: ChallengeType,
    val blockedReason: String?,
)

class ChallengePickerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow<List<ChallengeAvailability>>(
        ChallengeType.values().map { ChallengeAvailability(it, blockedReason = null) }
    )
    val state: StateFlow<List<ChallengeAvailability>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val wallet = db.walletDao().get() ?: Wallet()
            val walletRoom = Wallet.MAX_BALANCE_MINUTES - wallet.creditBalanceMinutes
            val today = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toEpochDay()

            _state.value = ChallengeType.values().map { type ->
                val cap = CreditRules.dailyCapMinutes(type)
                val remaining = cap?.let {
                    val already = db.challengeCompletionDao().creditsGrantedToday(type, today)
                    (it - already).coerceAtLeast(0)
                }
                val reason = when {
                    walletRoom <= 0 ->
                        "Your wallet is full at ${Wallet.MAX_BALANCE_MINUTES} minutes. Spend some before earning more."
                    remaining != null && remaining <= 0 ->
                        "You've earned all this one pays today."
                    else -> null
                }
                ChallengeAvailability(type, reason)
            }
        }
    }
}
```

- [ ] **Step 2: Write the picker**

Create `app/src/main/java/com/example/jikan/ui/challenge/ChallengePickerScreen.kt`:

```kotlin
package com.example.jikan.ui.challenge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jikan.challenge.CreditRules
import com.example.jikan.data.ChallengeType
import com.example.jikan.ui.theme.IconChip
import com.example.jikan.ui.theme.NeoCard

private data class ChallengeOption(
    val type: ChallengeType,
    val title: String,
    val requirement: String,
    val payout: String,
)

private val options = listOf(
    ChallengeOption(ChallengeType.STUDY, "Hiragana lesson", "10 cards", "up to 25 min"),
    ChallengeOption(
        ChallengeType.PUSHUPS,
        "Push-ups",
        "${CreditRules.PUSHUP_TARGET_REPS} reps",
        "+${CreditRules.PUSHUP_MINUTES} min",
    ),
    ChallengeOption(
        ChallengeType.STEPS,
        "Walk",
        "${CreditRules.STEPS_TARGET} steps",
        "+${CreditRules.STEPS_MINUTES} min",
    ),
    ChallengeOption(
        ChallengeType.PAUSE,
        "Mindful pause",
        "${CreditRules.PAUSE_TARGET_SECONDS} seconds",
        "+${CreditRules.PAUSE_MINUTES} min",
    ),
)

@Composable
fun ChallengePickerScreen(
    onPick: (ChallengeType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Earn time",
    viewModel: ChallengePickerViewModel = viewModel(),
) {
    val availability by viewModel.state.collectAsState()

    // Caps move as challenges complete, so re-read whenever the picker comes back up.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        IconChip(onClick = onBack) {
            Text("‹", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        options.forEach { option ->
            val blockedReason = availability.firstOrNull { it.type == option.type }?.blockedReason
            val enabled = blockedReason == null

            NeoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (enabled) Modifier.clickable { onPick(option.type) } else Modifier
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = blockedReason ?: option.requirement,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (enabled) option.payout else "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/jikan/ui/challenge/ChallengePickerViewModel.kt app/src/main/java/com/example/jikan/ui/challenge/ChallengePickerScreen.kt
git commit -m "feat: add the challenge picker with live cap availability"
```

---

### Task 14: Shared challenge host and navigation

The spec calls for one `ChallengeHost` used by both activities. `MainActivity` and
`LockActivity` each hand-roll their own navigation today, and adding four routes to both
would duplicate the same flow twice.

**Files:**
- Create: `app/src/main/java/com/example/jikan/ui/challenge/ChallengeHost.kt`
- Modify: `app/src/main/java/com/example/jikan/MainActivity.kt`
- Modify: `app/src/main/java/com/example/jikan/ui/lock/LockActivity.kt`
- Modify: `app/src/main/java/com/example/jikan/ui/lock/LockScreen.kt`
- Modify: `app/src/main/java/com/example/jikan/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `ChallengePickerScreen` (Task 13), `PushUpScreen` (Task 10), `PauseScreen` (Task 11), `StepsScreen` (Task 12).
- Produces: `@Composable fun ChallengeHost(onStudy: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier, title: String = "Earn time")`; `HomeScreen`/`HomeScreenContent` gain `onOpenChallenges: () -> Unit`; `LockScreen`'s `onStartLesson` becomes `onEarn: () -> Unit`.

- [ ] **Step 1: Write the host**

Create `app/src/main/java/com/example/jikan/ui/challenge/ChallengeHost.kt`:

```kotlin
package com.example.jikan.ui.challenge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.jikan.data.ChallengeType

/**
 * Owns the picker-to-challenge flow so MainActivity and LockActivity don't each
 * hand-roll it.
 *
 * Study is delegated through [onStudy] rather than hosted here, because the two callers
 * treat it differently: Home runs the session in place, while the lock screen finishes
 * its activity when the session ends.
 */
@Composable
fun ChallengeHost(
    onStudy: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Earn time",
) {
    var picked by remember { mutableStateOf<ChallengeType?>(null) }

    when (picked) {
        null -> ChallengePickerScreen(
            modifier = modifier,
            title = title,
            onPick = { type -> if (type == ChallengeType.STUDY) onStudy() else picked = type },
            onBack = onExit,
        )

        ChallengeType.PUSHUPS -> PushUpScreen(modifier = modifier, onDone = onExit)
        ChallengeType.PAUSE -> PauseScreen(modifier = modifier, onDone = onExit)
        ChallengeType.STEPS -> StepsScreen(modifier = modifier, onDone = onExit)
        ChallengeType.STUDY -> Unit
    }
}
```

- [ ] **Step 2: Add the single route to MainActivity**

In `app/src/main/java/com/example/jikan/MainActivity.kt`, extend the route enum with one
entry rather than four:

```kotlin
private enum class HomeRoute { HOME, STUDY, LOCKED_APPS, CHALLENGES }
```

Add the branch to the `when (route)` block:

```kotlin
            HomeRoute.CHALLENGES -> ChallengeHost(
                modifier = modifier,
                onStudy = {
                    studySessionKey++
                    route = HomeRoute.STUDY
                },
                onExit = { route = HomeRoute.HOME },
            )
```

Pass `onOpenChallenges = { route = HomeRoute.CHALLENGES }` to `HomeScreen`, and add
`import com.example.jikan.ui.challenge.ChallengeHost`.

- [ ] **Step 3: Add the secondary action to Home**

In `app/src/main/java/com/example/jikan/ui/home/HomeScreen.kt`, add
`onOpenChallenges: () -> Unit` to both `HomeScreen` and `HomeScreenContent` and pass it
through, then directly after the existing `PillButton`:

```kotlin
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Other ways to earn  →",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onOpenChallenges)
                .padding(8.dp),
        )
```

Add `onOpenChallenges = {}` to the `@Preview` call.

- [ ] **Step 4: Offer every challenge on the lock screen**

In `app/src/main/java/com/example/jikan/ui/lock/LockScreen.kt`, change the parameter
`onStartLesson: () -> Unit` to `onEarn: () -> Unit`, change the body copy from the
lesson-specific line to `"Earn a few minutes to unlock it."`, and change the button text
from `"Start lesson"` to `"Earn time"`, calling `onEarn`. Add
`import com.example.jikan.data.ChallengeType` only if the preview needs it; otherwise no
new import. Update the `@Preview` to pass `onEarn = {}`.

In `app/src/main/java/com/example/jikan/ui/lock/LockActivity.kt`, hold one flag and swap
between the lock screen and the host:

```kotlin
            var earning by remember { mutableStateOf(false) }

            if (earning) {
                ChallengeHost(
                    title = "Ways to unlock",
                    onStudy = { /* existing study-session route for this activity */ },
                    onExit = { earning = false },
                )
            } else {
                LockScreen(
                    blockedAppLabel = blockedAppLabel,
                    walletBalanceMinutes = walletBalance,
                    onEarn = { earning = true },
                )
            }
```

Wire `onStudy` to whatever this activity already does when a lesson is requested, and add
imports for `androidx.compose.runtime.mutableStateOf`, `remember`, `getValue`, `setValue`
and `com.example.jikan.ui.challenge.ChallengeHost`.

- [ ] **Step 5: Build and smoke-test on the device**

```bash
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Re-enable accessibility (installing revokes it on MIUI), then by hand: Home → "Other ways
to earn" → each of the three challenges completes and shows the award screen; drain the
wallet to 0, open a locked app, and confirm the lock screen offers all four options.
Complete push-ups seven times in a day and confirm the picker greys the option out with
"You've earned all this one pays today" **before** you start an eighth.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add the shared challenge host and wire it into Home and the lock screen"
```

---

### Task 15: Show the productivity level

Replaces the placeholders left in Task 6 Step 7.

**Files:**
- Modify: `app/src/main/java/com/example/jikan/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/jikan/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/example/jikan/widget/JikanWidget.kt`

**Interfaces:**
- Consumes: `ProductivityRepository` (Task 8), `ProductivityScore.label` (Task 3).
- Produces: `HomeUiState` gains `productivity: ProductivityResult?`; the widget's second stat becomes the level.

- [ ] **Step 1: Expose the level from the ViewModel**

In `HomeViewModel.kt`, add `val productivity: ProductivityResult? = null` to `HomeUiState`,
construct the repository beside the others, and collect it in its own coroutine so a slow
query never stalls the rest of the screen:

```kotlin
    private val productivityRepository = ProductivityRepository(
        appUsageDao = db.appUsageDao(),
        completionDao = db.challengeCompletionDao(),
        protectedDayDao = db.protectedDayDao(),
    )

    private fun observeProductivity() {
        viewModelScope.launch {
            productivityRepository.observe().collect { result ->
                _state.update { it.copy(productivity = result) }
            }
        }
    }
```

Call `observeProductivity()` from `init`. Add the imports for `ProductivityRepository` and
`ProductivityResult`.

- [ ] **Step 2: Render it on Home**

In `HomeScreen.kt`, replace the placeholder `LedgerStat` from Task 6 Step 7 with:

```kotlin
            val productivity = state.productivity
            LedgerStat(
                value = productivity?.score?.toString() ?: "—",
                unit = productivity?.let { ProductivityScore.label(it.band) } ?: "no history yet",
                label = "LEVEL",
                valueColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
```

Directly below the `StatLedger` call, add the honesty line:

```kotlin
        val unobserved = state.productivity?.unobservedDays ?: 0
        if (unobserved > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (unobserved == 1) {
                    "1 day this week wasn't measured — Jikan wasn't running."
                } else {
                    "$unobserved days this week weren't measured — Jikan wasn't running."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```

Add `import com.example.jikan.productivity.ProductivityScore` and update the `@Preview` to
pass a `productivity = ProductivityResult(score = 72, band = ProductivityBand.FOCUSED, observedDays = 7, unobservedDays = 0)`.

- [ ] **Step 3: Show it on the widget**

In `widget/JikanWidget.kt`, replace the placeholder stat. In `provideGlance`, read the
level alongside the wallet:

```kotlin
        val db = AppDatabase.getInstance(context)
        val wallet = db.walletDao().get() ?: Wallet()
        val productivity = ProductivityRepository(
            appUsageDao = db.appUsageDao(),
            completionDao = db.challengeCompletionDao(),
            protectedDayDao = db.protectedDayDao(),
        ).observe().first()

        provideContent {
            WidgetContent(
                walletMinutes = wallet.creditBalanceMinutes,
                level = productivity?.score,
            )
        }
```

Change `WidgetContent`'s second parameter to `level: Int?` and its stat to:

```kotlin
                    StatColumn(
                        label = "LEVEL",
                        value = level?.toString() ?: "—",
                        valueColor = WidgetVermillion,
                    )
```

Add imports for `ProductivityRepository` and `kotlinx.coroutines.flow.first`.

- [ ] **Step 4: Build, install, and confirm on device**

```bash
./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c && adb shell am force-stop com.example.jikan
adb shell am start -n com.example.jikan/.MainActivity
sleep 5 && adb logcat -d -s "AndroidRuntime:E"
adb shell screencap -p /sdcard/level.png && adb pull /sdcard/level.png
```

Expected: no crash; Home shows a LEVEL value or "no history yet"; the widget's second stat
reads LEVEL.

- [ ] **Step 5: Run the whole suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, including the pre-existing `SrsEngineTest`, `QuizGeneratorTest`,
`RedirectGateTest` and `OemBatteryHintsTest`.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: show the productivity level on Home and the widget"
```

---

## Verification

After Task 15, the following must all hold:

- `./gradlew :app:testDebugUnitTest` passes, with new coverage for the rep counter, the
  rate table, the award funnel and the productivity formula.
- The v4 migration preserved the wallet balance, card progress, locked apps and user name
  recorded in Task 4 Step 1.
- All four challenges complete on the device and produce an award screen.
- `protected_days` accumulates minutes while the service runs.
- Home and the widget show the productivity level; no reference to a streak remains
  (`grep -rn "streak\|Streak" app/src/main --include=*.kt` returns nothing).
