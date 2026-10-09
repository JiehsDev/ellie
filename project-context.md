# Jikan Project Context

This file is the handoff brief for any AI or human contributor working on Jikan. Read it before changing code.

## Product Vision

Jikan is an Android app that helps a student trade focused Japanese study for intentional access to distracting apps.

The core loop is simple:

1. The user chooses distracting apps to lock.
2. Jikan blocks those apps when the user's time wallet is empty.
3. The user studies Japanese, starting with hiragana.
4. Correct answers earn minutes.
5. Earned minutes can be spent on any locked app.

Jikan should feel firm but kind. It is not a punishment app. It is a study companion and app-lock guardian that helps the user pause, learn, and return to apps with intention.

The current product personality is embodied by **Jikan Coach**, a mascot shown through `JikanCoachMessage`. The coach should feel observant, warm, calm, slightly playful, and emotionally present. It should never shame the user, guilt-trip them, exaggerate danger, or pressure them into excessive studying.

## Product Inspirations

Pattern references:

- Duolingo-style bite-sized lessons, quiz feedback, progress, and streak motivation.
- Native Android app-lock flows, using redirect rather than overlays.
- Tarsi by Bryl Lim as a reference for an interactable AI-like mascot inside a utility app.

Do not clone these products. Use them as pattern references for quality, tone, and interaction design.

## Target Platform

Android only for now.

The app is Philippines-focused, where Android is the practical target. iOS is deferred because Screen Time / Family Controls would require a different implementation.

Current app constraints:

- Kotlin + Jetpack Compose.
- Room for local persistence.
- Coroutines + Flow for reactive state.
- AccessibilityService for app locking.
- WorkManager for background checks and expiry tasks.
- Local-first/offline-first.
- No backend required for MVP.
- Current min SDK is API 29 because the local llama.cpp Android binding targets API 29+ and arm64.
- Current native AI packaging is arm64-v8a only.

## Core Economy

The wallet is a universal time balance.

- 1 minute in the wallet can be spent on any locked app.
- Credits are earned from study sessions.
- Credit awards are proportional to correct answers, not pass/fail only.
- Missed cards should be queued for review through SRS.
- Perfect score gives a bonus.
- Streaks slightly improve earning.
- Daily grinding should have diminishing returns.
- Wallet has a max cap, currently `Wallet.MAX_BALANCE_MINUTES`.

The user should always understand why they earned or did not earn minutes.

## Study Scope

MVP study content is hiragana recognition.

Implemented/expected direction:

- Hiragana cards are seeded locally.
- Lesson screen shows kana, audio, romaji/mnemonic style learning.
- Quiz screen uses multiple choice.
- SRS is simplified SM-2-like logic.
- Wrong answers should be reinforced.

Deferred:

- Katakana.
- Vocab.
- Kanji/radicals.
- Stroke-order tracing.
- Social/accountability features.

## App Locking Model

Jikan uses **redirect**, not overlay.

Reason:

- Feels more native.
- Avoids `SYSTEM_ALERT_WINDOW`.
- Less malware-like.
- Easier to reason about.

Mechanism:

- `AppLockAccessibilityService` watches foreground app changes.
- If a locked app opens and no wallet time is available, Jikan launches its own lock flow.
- The user studies to earn minutes or returns home.
- Brief flashes of the blocked app are acceptable trade-offs.

Important reliability areas:

- Accessibility service can be killed or disabled by Android/OEM behavior.
- Jikan must show protection health clearly.
- Re-check protection on app resume.
- Watch out for Xiaomi/Oppo/Vivo-style battery restrictions.

## Banking Mode And Strict Mode

Banking mode exists so the user can temporarily disable protection for sensitive or necessary tasks.

Tone:

- Be transparent.
- Do not hide that protection is off.
- Coach should gently remind the user to turn protection back on.

Strict/protection health states should always win over playful messages. If protection is off, the user needs clear safety-state information first.

## Phase 4: Generic App Restrictions

Implemented 2026-10-09. App restrictions are user-configured daily screen-time
limits per app, independent of locked-app tiers and earn rules.

- `data/AppRestriction` (`app_restrictions` table, DB v13): `packageName`,
  `appLabel`, `dailyLimitMinutes`, `enabled`.
- `screentime/AppRestrictionPolicy`: pure-Kotlin deterministic policy answering
  "Is this package currently allowed to be opened?" Precedence: protection off
  > paused > banking exempt > existing lock rule (wallet>0 allowed, else
  blocked with tier) > restriction (under limit allowed; at/over limit allowed
  only via generic wallet extension, hard-blocked in strict mode) > no
  restriction. AI may explain a decision (`RestrictionDecision.explain`); it
  must never make one.
- `AppLockAccessibilityService`: non-locked packages are evaluated against
  their restriction using UsageStatsManager foreground minutes (60 s cache,
  throttled evaluation). Over-limit blocks redirect through the existing
  `RedirectGate` → `LockActivity` path. The spend tick also covers
  wallet-extended restricted apps (1 min/min, recorded in `app_usage`).
  Existing locked-app behavior is untouched.
- Tests: `AppRestrictionPolicyTest` (18 cases: below/at/over limit, protection
  off, paused, banking, strict, wallet states, lock-rule precedence,
  multi-app independence, explanations).

Not yet built: UI for configuring restrictions (no Home redesign in this
phase); restrictions require usage-access permission, and fail open without it.

## Phase 5: Screen-Time Analytics

Implemented 2026-10-09. The existing `screentime` domain
(`ScreenTimeCalculator` / `ScreenTimeSummary` / `ScreenTimeRepository`) is the
single analytics layer — extended, not duplicated.

- `ScreenTimeSummary` gains: `walletBalanceMinutes`, `approachingApps` /
  `atLimitApps` (80%-of-limit and exactly-at-limit bands; exceeded keeps the
  existing `exceededApps`), `averageUsageChange` (today vs recent average),
  `weeklyTrend` (last 7 days, oldest first), `earningApps`.
- Limit classification is deterministic: APPROACHING = usage in [80%, 100%)
  via integer math, AT_LIMIT = exactly at, EXCEEDED = over. AI receives the
  summary as structured facts and must not recompute the arithmetic.
- `ScreenTimeRepository` now sources configured limits from the
  `app_restrictions` table (Phase 4) instead of the old locked-tier heuristic —
  one source of truth for limits. "Restricted" for minutes/status = locked OR
  limit-configured.
- Tests: `ScreenTimeAnalyticsTest` (22 cases: percentage rounding, zero usage,
  yesterday comparison, weekly averages, top-app sorting, limit
  classification, earning/spending totals, weekly trend).

## Phase 6: Coach Reoriented Around Screen Time

Implemented 2026-10-09. Jikan Coach is now a screen-time companion. Existing
architecture preserved and reused: `JikanCoachMessage`, `CoachInput`,
`CoachEvent`, `CoachMood`, `AiCoach`, `RealAiCoach`, `FakeAiCoach` all kept.

- `CoachInput` gains one field: `screenTime: ScreenTimeSummary?` — the Phase 5
  structured facts. All arithmetic stays in the screentime package; AI only
  interprets facts and never decides blocks, protection, wallet, or usage.
- `CoachEvent` gains: AppLimitApproaching/Reached/Exceeded,
  ProtectionDisabled/Restored, HighScreenTime, UsageImproved/Increased,
  EarningCreditGranted, DailySummary.
- `HomeCoachMessageProvider` priority: banking/protection safety first, then
  screen-time events, then passive observations, then the preserved
  study/wallet/morning fallbacks. Tone: calm, observant, supportive, slightly
  playful; max two sentences; never judgmental; no invented statistics.
  Deterministic — no local model required.
- `HomeViewModel` feeds the Phase 5 summary into the coach (refreshed on
  entry/resume and every 60 s) and derives the headline event from it.
- `AiPromptBuilder.buildScreenTimeInsightPrompt` packages the facts with the
  safety guardrails for optional model use.
- Tests: `ScreenTimeCoachTest` (25 cases: safety priority, exact copy per
  event, tone constraints incl. banned-word sweep, event derivation,
  mood) plus a prompt-builder test. Note: the protection-off message was
  reworded per the Phase 6 spec ("Protection is off right now. Jikan won't be
  able to enforce your limits until you turn it back on."); the one existing
  test asserting the old copy was updated.

## Phase 7: Local AI Adapted for Screen Time

Implemented 2026-10-09. Audit verdict: no architecture replacement needed —
the `InferenceEngine` interface already abstracts the llama.cpp runtime, so it
(and `LlamaCppEngine`, `ModelManager`) stay untouched apart from one added
stop sequence. `AiCoach`, `RealAiCoach`, `FakeAiCoach`, `InferenceEngine`,
`AiPromptBuilder` all preserved and extended.

- `ScreenTimeContext` is a typealias for `ScreenTimeSummary`: the model gets
  structured facts only, never raw usage events, never arithmetic to do.
- `AiCoach.insightForScreenTime(context, protectionEnabled)`: new interface
  method, implemented by both coaches.
- `RealAiCoach`: 30 s inference timeout; `InsightSanitizer` validates output
  (blank/echo/rambling trimmed, max 2 sentences / 280 chars). All five failure
  modes — unavailable, timeout, inference failure, malformed, empty — fall
  back to the deterministic provider via `FakeAiCoach` (now wired as the
  default fallback).
- `AiPromptBuilder.buildScreenTimeInsightPrompt` aligned to the spec: "You
  are Jikan Coach, a calm screen-time companion.", use-only-supplied-facts,
  never invent/calculate/shame/diagnose, never safety decisions, never
  override deterministic restrictions, 1–2 sentences.
- `HomeCoachMessageProvider.toCoachInput`: one mapping from context to
  deterministic input, no duplication.
- Tests: `ScreenTimeAiTest` (17 cases incl. all fallback paths with scripted
  engines) + extended `AiPromptBuilderTest`. Home keeps its deterministic
  coach path; the AI path is ready but not yet wired into UI (per "do not
  redesign UI yet").

## Phase 8: Jikan Home Dashboard

Implemented 2026-10-09. Home reoriented as a screen-time control center while
preserving the existing design language (NeoCard, SectionHeader, pills, warm
palette) and all existing behavior components.

New hierarchy in `HomeScreenContent`: greeting → coach bubble → protection
pill/banners → `ScreenTimeHero` ("Today / 3h 21m / ↓ 18m vs yesterday") →
`WalletPanel` → primary "Study now" action → `TopAppsSection` (label + minutes
+ slim bars) → `EarningSection` ("+15m earned · 20m spent" + per-app usage) →
`AppLimitsSection` (exceeded → at-limit → approaching rows with status dots)
→ locked apps → quick actions → `ScreenTimeWeekStrip` → theme selector.

- Screen-time sections render only when `state.screenTimeSummary` is non-null
  (usage access granted); no ViewModel changes were needed.
- Mascot kept as the existing static image, interacting with the coach bubble
  through composition (no Lottie/GIF/video/new assets).
- The study-centric `StatLedger` (streak/spent/learned) was removed from Home;
  `WeekStrip` now shows screen-time minutes via a title parameter.
- Wallet logic, accessibility logic, and AI architecture untouched.
- Preview updated with representative screen-time data.

## Jikan Coach

Jikan Coach is the mascot/personality layer.

Existing component:

- `com.example.jikan.ui.coach.JikanCoachMessage`
- Reuse this visual component. Do not redesign it casually.
- It supports an optional click handler.

Rule-based home/event brain:

- `com.example.jikan.ui.home.HomeCoachMessageProvider`
- `CoachInput`
- `CoachEvent`
- `CoachMood`

The coach is currently rule-based for Home, Lock, and Results screens. This is intentional. Important product states must be deterministic and safe.

Coach personality:

- Calm.
- Observant.
- Supportive.
- Slightly playful.
- Patient.
- Never judgmental.
- Never guilt/shame/fear based.
- No excessive emojis.
- Two sentences max for most UI messages.

Examples of the desired voice:

- "I see the Loopy urge. Very human. Earn a few minutes first, then go be mysterious online."
- "That was clean work: 10/10. I am quietly proud, which is still proud."
- "I can't guard your apps right now. Turn Jikan back on in Accessibility and I'll take watch again."

Coach rules:

- Protection/banking warnings have priority over streak/wallet/fun messages.
- Do not call AI/inference for safety-critical text.
- Prefer local rule-based messages for frequent UI states.
- Use AI only as a soft rewrite/summary layer when failure is safe.
- If local AI fails, fallback copy must still be useful.

## Local AI Direction

Jikan has an experimental local AI path for coach summaries.

Current model target:

- Hugging Face repo: `mfuntowicz/SmolLM2-360M-Instruct-Q4_K_M-GGUF`
- File: `smollm2-360m-instruct-q4_k_m.gguf`
- Version label: `SmolLM2-360M-Instruct-Q4_K_M`

Current implementation pieces:

- `AiCoach`
- `RealAiCoach`
- `FakeAiCoach`
- `InferenceEngine`
- `LlamaCppEngine`
- `ModelManager`
- `AiPromptBuilder`
- `AiInsightEntity` / DAO

Native runtime:

- Vendored submodule: `libs/llama.kt`
- Gradle module: `:llama-kt`
- CPU-only CMake patch currently applied for reliable builds.
- OpenCL/Vulkan acceleration was disabled because generated shaders/headers were missing.

Important:

- The app can compile/package local llama support.
- On-device generation still needs real-device validation with model download, memory, latency, and output quality.
- Do not make core UX depend entirely on local AI.
- Keep deterministic fallback copy.

## Main Architecture Map

Important packages:

- `data/`: Room entities, DAOs, database, repository.
- `srs/`: spaced repetition logic.
- `study/`: session state, quiz generation, credit calculation.
- `ui/home/`: dashboard state, coach provider, home screen.
- `ui/coach/`: mascot message visual.
- `ui/lesson/`: lesson screen.
- `ui/quiz/`: quiz screen.
- `ui/results/`: session results.
- `ui/lock/`: redirect/lock screen.
- `ui/onboarding/`: setup flow.
- `ui/settings/`: locked app and protection settings.
- `service/`: accessibility, pause/banking, watchdog workers.
- `widget/`: app widget.
- `coach/`: local AI interfaces and inference path.

## Current Home Dashboard Direction

Home should be the user's control room:

- Greeting.
- Jikan Coach message.
- Protection/banking/strict state.
- Wallet dial.
- Study CTA.
- Stats ledger.
- Locked app shortcuts/status.
- Week strip.
- Theme selector currently exists.

Keep the dashboard practical. Avoid turning it into a marketing page.

## UI Design Guidance

Jikan should feel warm, focused, and companion-like, not corporate.

Use:

- Calm but distinctive typography.
- Clear hierarchy.
- Dense enough information for repeated use.
- The mascot for emotional/contextual support.
- Neumorphic-ish app styling only where already established by local components.

Avoid:

- Guilt copy.
- Cluttered dashboards.
- Excessive cards inside cards.
- Decorative blobs/orbs.
- Big marketing hero sections.
- Rebuilding existing components without need.

## Contribution Rules

Before changing code:

1. Read this file.
2. Inspect the existing implementation.
3. Preserve user changes and uncommitted work.
4. Prefer small, local changes.
5. Add or update tests for pure logic.
6. Run relevant Gradle checks.

Preferred checks:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Use `assembleDebug` after changes touching:

- Compose UI with resources.
- Manifest.
- Gradle.
- Native llama module.
- Accessibility/service declarations.

## Testing Priorities

Highest risk areas:

- Accessibility redirect reliability on real devices.
- OEM battery/autostart behavior.
- Banking mode transitions and protection recovery.
- Strict mode false positives.
- Wallet accounting.
- SRS scheduling.
- Local model download/load/generation on real arm64 devices.

Unit-testable areas:

- `CreditCalculator`
- `SrsEngine`
- `QuizGenerator`
- `HomeCoachMessageProvider`
- `AiPromptBuilder`
- protection/status calculators

## Agent Notes

If you are an AI agent working here:

- Do not invent backend infrastructure unless asked.
- Do not replace local rule-based safety messages with generative AI.
- Do not remove fallback behavior.
- Do not casually raise app complexity.
- Do not revert unrelated dirty files.
- Keep Jikan Coach emotionally intelligent, not noisy.
- When in doubt, make the app more trustworthy, clearer, and kinder.

The north star: **Jikan helps the user interrupt distraction with a tiny act of learning, then gives time back with dignity.**
