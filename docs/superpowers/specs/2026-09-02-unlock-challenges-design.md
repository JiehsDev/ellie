# Unlock Challenges and the Productivity Level

**Status:** approved design, not yet implemented
**Date:** 2026-09-02

## Context

Today the only way to earn phone time in Jikan is a hiragana study session.
`StudySessionViewModel` drives the flow, `StudyRepository.completeSession()` computes
credits and writes them to the wallet, and everything else — the lock screen, the
widget, Home — assumes that single path.

This design adds three more ways to earn the same currency, and replaces the study
streak with a measured productivity level.

## Goals

- Let a user earn wallet minutes through push-ups, a walk, or a mindful pause, not
  only through studying.
- Keep one economy: every challenge pays into the same wallet, spent the same way.
- Replace the all-or-nothing streak with a rolling score that degrades and recovers.
- Make the score honest, including when Jikan cannot see what the user is doing.
- Keep the earning rules unit-testable rather than verifiable only by hand on a phone.

## Non-goals

Deferred to a separate production-readiness spec:

- Google Play policy work. Play scrutinises `AccessibilityService` used for
  non-accessibility purposes, and app-lockers sit squarely in that blast radius. The
  alternative implementation (`UsageStatsManager` plus an overlay) and the current
  policy both need proper investigation before store submission. This is the single
  largest open risk to shipping and it is not addressed here.
- Crash reporting, privacy policy, data export, battery-optimisation survival.

Explicitly out of scope for this design:

- Accelerometer rep counting (squats, shakes) — rejected during brainstorming for
  false-positive rate and per-device tuning cost.
- Per-app challenge assignment ("Instagram always costs 10 push-ups") — rejected in
  favour of the single shared wallet.
- Camera or ML pose detection.

## Decisions taken

| Decision | Choice | Why |
|---|---|---|
| Unlock model | All challenges pay one wallet | One balance, one dial; challenges are interchangeable ways to buy time |
| v1 challenges | Push-ups, mindful pause, walk | Cheapest real physical challenge, cheapest friction, genuine off-the-couch lever |
| Enforcement | Firm but fair | Plausibility checks and daily caps, no dark patterns, quitting always allowed |
| Streak | Removed, replaced by productivity level | A streak resets to zero on one miss, and after a reset there is nothing left to protect |

## Architecture

Each challenge keeps its own screen and ViewModel — an SRS card flow, a proximity
state machine and a countdown have almost nothing worth factoring together. What they
share is the ending: every challenge finishes by calling one funnel that mints credits.

```
PushUpViewModel  ─┐
PauseViewModel   ─┤
StepsViewModel   ─┼──> ChallengeRepository.award(type, metric, earnedMinutes)
StudySessionVM   ─┘         │
                            ├─ apply per-type daily cap
                            ├─ apply 60-minute wallet ceiling
                            ├─ write challenge_completions row
                            ├─ update wallet
                            └─ refresh widget
```

`StudyRepository.completeSession()` keeps owning SRS scheduling and the `Session`
row, but hands its final wallet write to the funnel. One place mints credits.

### ChallengeRepository

```kotlin
enum class CapReason { DAILY_CAP, WALLET_FULL }

data class ChallengeAward(
    val type: ChallengeType,
    val metric: Int,
    val earned: Int,          // what the rate says, before limits
    val granted: Int,         // what actually reached the wallet
    val walletBalance: Int,
    val cappedBy: CapReason?,
)

class ChallengeRepository(
    private val walletDao: WalletDao,
    private val completionDao: ChallengeCompletionDao,
) {
    suspend fun award(type: ChallengeType, metric: Int, earnedMinutes: Int): ChallengeAward
}
```

Callers compute `earnedMinutes` themselves: `CreditRules.earnedFor(type, metric)` for
the three flat-rate challenges, the existing `CreditCalculator` for study.

Cap arithmetic, in order:

```
alreadyToday  = completionDao.creditsGrantedToday(type, today)
capRemaining  = DAILY_CAP[type] - alreadyToday      // STUDY has no cap here
afterCap      = min(earnedMinutes, capRemaining)
walletRoom    = Wallet.MAX_BALANCE_MINUTES - currentBalance
granted       = max(0, min(afterCap, walletRoom))
```

`cappedBy` is `WALLET_FULL` when `walletRoom` is the binding constraint, `DAILY_CAP`
when the cap is, and null when the user got everything they earned. The results
screen must surface this: doing ten push-ups and receiving two minutes without
explanation reads as a bug.

Study keeps no cap in the funnel because `CreditCalculator` already decays repeat
sessions within a day.

## Data model

Schema version 3 → 4, with a hand-written migration. The v3 migration is the
precedent: write the SQL, diff it against Room's generated `CREATE TABLE` in
`app/build/generated/ksp/debug/kotlin/.../AppDatabase_Impl.kt`, then confirm on device
that existing rows survived.

### New

```kotlin
enum class ChallengeType { STUDY, PUSHUPS, PAUSE, STEPS }

@Entity(tableName = "challenge_completions", indices = [Index("epochDay")])
data class ChallengeCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ChallengeType,
    val epochDay: Long,
    val completedAt: Long,
    val metric: Int,              // reps, steps, seconds, or correct answers
    val creditsEarned: Int,       // before caps — this is effort
    val creditsGranted: Int,      // after caps — this is payout
)

@Entity(tableName = "protected_days")
data class ProtectedDay(
    @PrimaryKey val epochDay: Long,
    val observedMinutes: Int,
)
```

`ChallengeType` needs converters in `Converters.kt`, following the existing
`CardTier` / `DailyGoalPreset` pattern.

Storing effort and payout separately matters: the productivity score measures effort,
so a user whose wallet happened to be full is not scored as having done nothing.

### Changed

`Wallet` loses `currentStreakDays` and `lastStudyEpochDay`. SQLite cannot drop
columns at this level, so the migration recreates the table:

```sql
CREATE TABLE wallet_new (<column list copied verbatim from the generated schema>);
INSERT INTO wallet_new SELECT id, creditBalanceMinutes, lifetimeCreditsEarned FROM wallet;
DROP TABLE wallet;
ALTER TABLE wallet_new RENAME TO wallet;
```

The column list is deliberately not written out here: it must be copied from Room's
generated `CREATE TABLE` for the v4 `Wallet` rather than hand-authored, or the
identity check fails at open time and the app crashes on launch.

This is the riskiest step in the migration and gets verified against the generated
schema before it ships.

`CreditCalculator`'s streak bonus is re-pointed at the new 7-day consistency figure,
so regularity still pays but stops being all-or-nothing.

## Credit rates

Rates are set by **return** — minutes earned per minute of real effort — not by gut
feel, because an unbalanced table quietly makes some challenges pointless.

| Challenge | Requirement | Real effort | Earns | Return | Daily cap |
|---|---|---|---|---|---|
| Hiragana lesson | 10 cards | ~3–4 min | ~8–25 min via `CreditCalculator` | ~4:1 | existing per-session decay |
| Push-ups | 10 reps | ~30 s | 3 min | 6:1 | 20 min |
| Walk | 300 steps | ~3 min | 6 min | 2:1 | 20 min |
| Mindful pause | 60 s | 60 s | 2 min | 2:1 | 10 min |

An earlier draft paid 5 minutes for 10 push-ups and 6 for 500 steps, which put returns
at 10:1 and 1.2:1 — an eightfold spread. Push-ups beat studying by 2.5× despite the
stated intent that study lead, and walking was so poor nobody would ever choose it.
The table above compresses the spread to 3×.

Push-ups keep a modest edge over studying on purpose: physical effort per second is
genuinely higher than reading flashcards, so equal returns would undervalue them.

The caps imply roughly 67 push-ups, 1,000 steps or 5 pauses to max out a single
challenge in a day. The 60-minute wallet ceiling applies on top of every cap.

Rates and caps live in one `CreditRules` object so the table above is a single
readable thing rather than constants scattered across four ViewModels.

## Challenge mechanics

### Push-ups — proximity sensor

`Sensor.TYPE_PROXIMITY` reports near/far (most devices are binary; some report
centimetres, so readings are thresholded against `sensor.maximumRange`).

The counter is a **pure state machine** with no Android types, so the plausibility
rules are tested in JUnit rather than by doing push-ups on the floor:

```kotlin
class ProximityRepCounter(
    private val minRepMillis: Long = 800L,      // reps closer than this are rejected
    private val minNearMillis: Long = 250L,     // guards a flick past the sensor
    private val maxNearMillis: Long = 5_000L,   // phone set down, not a rep
) {
    fun onReading(isNear: Boolean, timestampMillis: Long): Int
    val reps: Int
}
```

A rep is `FAR → NEAR → FAR`, counted on the rising edge. It is discarded when the
near phase is shorter than `minNearMillis` or longer than `maxNearMillis`, and when
less than `minRepMillis` has passed since the last counted rep.

If the device has no proximity sensor the challenge is listed but unavailable, with
the reason shown.

### Mindful pause — timer

A 60-second countdown that **resets to full if the user leaves the app**, observed on
`Lifecycle.Event.ON_STOP` (not `ON_PAUSE`, which fires for dialogs and would reset
spuriously). The reset is the mechanic: without it the pause can be backgrounded and
farmed.

### Walk — step counter

`TYPE_STEP_COUNTER` is a cumulative hardware counter that keeps counting with the
screen off, so this needs no foreground service. The challenge records a baseline on
start and diffs it on return.

Baseline, target and start time live in SharedPreferences, not the database — this is
ephemeral in-progress state, not domain history, and it should not cost a migration.

Requires the `ACTIVITY_RECOGNITION` runtime permission on API 29+ (manifest entry
plus a runtime request). `minSdk` is 26, so the permission is requested conditionally.

A reboot resets the hardware counter to zero, which would make the delta negative. On
detecting `current < baseline`, the baseline resets to the current value, progress is
lost, and the user is told why.

## Productivity level

Replaces the streak everywhere it appeared: Home, the widget, and the
`CreditCalculator` bonus.

Three signals over a trailing 7 days, each normalised to 0–1:

```
restraint(d)  = max(0, 1 − lockedAppMinutes(d) / 90)     // observed days only
effort(d)     = min(1, creditsEarned(d) / 15)
consistency   = activeDays / 7                            // days with any completion

level = round(100 × (0.45·R̄ + 0.35·Ē + 0.20·C))
```

`R̄` and `Ē` are means over the window.

The restraint scale is **90 minutes, deliberately not 60**. Anchoring it at the
60-minute wallet ceiling looked natural and was wrong: a user who earns 60 minutes
legitimately and spends them would score zero on the heaviest-weighted term. Perfect
compliance would produce the worst possible restraint, and the score would contradict
the economy it sits on top of. At 90, a fully earned-and-spent day scores 0.33, and
you only reach zero by stacking passive regeneration or leaving protection off — which
is what a genuinely bad day looks like. The curve stays linear; only the anchor moved.

15 minutes is the effort target — roughly one solid lesson or two physical challenges
— and it is capped so a single 200-push-up binge cannot bank a week.

Bands: **Drifting** 0–19 · **Settling** 20–44 · **Steady** 45–64 · **Focused** 65–84
· **Locked in** 85–100.

Worked examples, which become the test fixtures:

| | Locked-app min/day | Credits/day | Active days | Level |
|---|---|---|---|---|
| Good week | 10, 5, 0, 15, 8, 12, 6 | 18, 12, 0, 20, 15, 10, 14 | 6/7 | 85 — Locked in |
| Heavy week | 55, 60, 48, 60, 52, 58, 45 | 8, 0, 0, 5, 0, 0, 6 | 3/7 | 33 — Settling |
| Bad week | 120, 95, 140, 110, 90, 130, 100 | 0, 5, 0, 0, 0, 0, 0 | 1/7 | 5 — Drifting |
| Installed, ignored | 0 × 7 | 0 × 7 | 0/7 | 45 — Steady |

Two of these rows are worth reading twice. The last is intentional: a user who never
opens a locked app is not doomscrolling, even if they never study, and the score
should say so — it assumes the service was running and saw a quiet week, which is a
different case from an *unobserved* quiet week, below.

"Heavy week" landing at 33 rather than the teens is the 90-minute anchor doing its
job. Averaging 54 minutes a day of locked-app time is not good, but it is close to
what the economy actually permits, so it reads as mid-low rather than as failure.
Reaching Drifting now takes sustained 90-plus-minute days, as the "Bad week" row
shows.

### Before there is enough history

A fresh install has no observed days and no completions. Running the formula there
gives 0 — "Drifting" — which would greet a new user by calling them a failure for
having just installed the app.

So the level is withheld until the window contains **at least one observed day or one
completion**. Until then Home shows "Not enough history yet" in place of a number.
This is also the correct state for a user who has installed Jikan but not yet enabled
the accessibility service, where the honest answer is that Jikan cannot see anything.

### Measurement integrity

The formula has one way to lie: **when the accessibility service is off, no usage is
recorded, so restraint reads as spotless while the user scrolls freely.** This is not
hypothetical — it happened twice during development, and both times Home looked
perfectly healthy.

The lock service already runs a 60-second tick while alive. That tick increments
`protected_days.observedMinutes` for today, unconditionally, giving a record of the
days Jikan was running at all.

The threshold detects **absence, not coverage**. This distinction decides the number:

- Doze suspends the handler when the screen is off, so observed minutes track phone
  use, not wall-clock time. A genuinely light day might record only 40 minutes.
- Those light days are the *best* restraint days. A high threshold would discard
  exactly the days the user did well, biasing the score down — the opposite of the
  intended correction.
- The failure being guarded against is "accessibility was off all day," which records
  approximately **zero**, not forty.

So:

- A day counts as **observed** when `observedMinutes >= 15`.
- Unobserved days are **excluded** from the restraint mean, never counted as spotless.
- When no day in the window is observed, the restraint term is dropped and the
  remaining weights are renormalised (effort 0.35/0.55, consistency 0.20/0.55).
- Home states how many days went unobserved rather than quietly inflating the score.

The threshold is deliberately not framed as certifying that Jikan watched a full day.
It cannot honestly claim that, and a design that pretends otherwise would be a
different kind of lie from the one it set out to fix.

## Surfaces

A shared `ChallengeHost` composable owns challenge routing and is used by **both**
`MainActivity` and `LockActivity`. Both currently hand-roll their own navigation
enums; adding four routes to each would duplicate the flow twice. This is a targeted
fix to existing structure, not general refactoring.

- **Home** — "Study now" stays the primary action. A secondary "Other ways to earn →"
  opens the challenge picker. Home is already dense; the picker keeps it from growing.
- **Lock screen** — currently a single "Start lesson" button. Becomes the picker
  inline, each option showing its payout and whether it is currently available.
- **Widget** — the streak stat becomes the productivity level.

## Error and empty states

Every one of these tells the user what happened and what to do, in the interface's
voice. None of them apologise or go vague.

| Situation | Copy |
|---|---|
| No proximity sensor | "This phone has no proximity sensor, so push-ups can't be counted." |
| Step permission denied | "Jikan needs activity permission to count steps." + Grant |
| Daily cap reached | "You've earned your 20 minutes from push-ups today." (option disabled) |
| Wallet full | "Your wallet is full at 60 minutes. Spend some before earning more." |
| Step counter reset | "Your phone restarted, so the step count restarted too." |

Cap and wallet-full states are shown **before** the user starts a challenge. Letting
someone do twenty push-ups for zero minutes is the failure this prevents.

## Testing

Written first, per TDD. All of the following run on the JVM with the existing
`testImplementation(libs.junit)`; none need a device.

**`ProximityRepCounter`** — clean reps counted; sub-`minRepMillis` cycles rejected;
flicks under `minNearMillis` rejected; phone-set-down over `maxNearMillis` discarded;
out-of-order and duplicate timestamps handled without crashing or double-counting.

**`CreditRules`** — the rate table, exactly as specified above.

**`ChallengeRepository.award`** — daily cap binding; wallet ceiling binding; both
binding at once with correct `cappedBy` precedence; neither binding; award at exactly
the cap boundary; `creditsEarned` and `creditsGranted` recorded independently.

**`ProductivityScore`** — the three worked examples above as fixtures; band
boundaries at 19/20, 44/45, 64/65, 84/85; unobserved-day exclusion; the all-unobserved
renormalisation; the empty-history case.

**Device smoke, after unit tests pass** — each challenge completed once end to end;
the v4 migration verified to preserve wallet balance, card progress, locked apps and
user name, exactly as the v3 migration was checked.

## Risks

1. **Wallet column drop.** The table-recreate migration is the most likely thing to
   break, and it breaks by wiping data. Mitigated by diffing generated SQL and
   verifying on-device rows before and after.
2. **Play policy on accessibility services** (see Non-goals). Unresolved, and it could
   invalidate the current locking approach entirely. Needs its own investigation
   before any store work.
3. **Proximity sensor variation.** Devices differ in whether they report binary or
   centimetre values, and in sampling rate. Thresholding against `maximumRange`
   handles the common cases; genuinely odd hardware degrades to "unavailable".
4. **Doze undercounting protected minutes**, addressed above by the low threshold.
