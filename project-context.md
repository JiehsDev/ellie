# Project: Study-to-Unlock (working title)

## Problem
Student user has a lot of academic work to do but is stuck in a habit of doomscrolling (TikTok, etc.) and playing games (e.g. Mobile Legends), which eats into study/focus time. Persona also wants to learn Japanese, so the solution ties the "unlock" mechanic directly to Japanese study rather than a generic focus timer.

## Target platform
Android only for now (dominant OS share among Filipino users — this is a Philippines-focused build). iOS deliberately deferred; Apple's Screen Time / Family Controls frameworks are more restrictive and would need a separate implementation pass later.

## Core concept
Distracting apps are locked by default. To unlock time on them, the user studies Japanese (starting with hiragana) and passes a quiz. Credits earned are a universal currency — 1 credit ≈ 1 minute, spendable on ANY locked app (not per-app separate balances).

## Credit economy rules
- Credits awarded **per correct answer**, not pass/fail gated (e.g. ~1.5–2 min per correct answer out of 10 questions per quiz).
- No credit for unanswered/skipped questions.
- Missed questions get queued into spaced repetition for the next session, not just discarded.
- Perfect score gives a small bonus.
- Streak bonus: consecutive days of study sessions increase earn-rate slightly (e.g. +10%).
- Soft daily cap / diminishing returns: grinding many sessions back-to-back in one day yields progressively fewer credits per session, to prevent pure credit-farming while still allowing free additional studying.
- "Keep studying" option added on the results screen (secondary button, below the primary "Continue to [app]" button) — lets a user who's in the groove chain directly into another lesson without routing back through the lock/redirect screen.

## Content & SRS structure
- Progression tiers: Hiragana (Set 1 vowels → k-row → s-row... full 46) → Katakana → basic vocab using only learned kana → basic kanji radicals → short vocab/phrases.
- MVP scope: **ship hiragana only first**, recognition-based quiz types only. Defer katakana/vocab tiers and stroke-order tracing until the core loop is validated.
- Each card: character, romaji, audio clip, stroke order data (deferred for MVP), optional mnemonic, tier.
- SRS: simplified SM-2 (like Anki). Correct answer → interval grows (1 day → 3 → 7 → 14...). Wrong answer → interval resets, card requeued within the same session for immediate reinforcement.
- Quiz question types (MVP): character→sound, sound→character (multiple choice / tap). Stroke tracing deferred (complex to build/validate).
- Runs fully offline — no backend needed for content or SRS state.

## App-locking mechanism (decided: redirect, not overlay)
Originally considered a full-screen overlay (`TYPE_APPLICATION_OVERLAY`) drawn on top of the blocked app. **Decision: use redirect instead** — feels more native, avoids `SYSTEM_ALERT_WINDOW` permission, less "malware-like" UX. Overlay was ruled out specifically for hurting the experience.

Mechanism:
- `AccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED` events.
- When a blocked package comes to foreground and wallet balance <= 0, fire an `Intent` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` to launch the app's own lock/lesson screen.
- Trade-off accepted: brief flash (a few hundred ms) of the blocked app before redirect fires, in exchange for a more native feel and simpler implementation.

**Back-button handling:** Override `onBackPressed()` on the lock screen to call `moveTaskToBack(true)` (send to home), not back into the blocked app's stack (which is already cleared via `FLAG_ACTIVITY_CLEAR_TOP` anyway).

**Reopen race mitigation:**
- Don't rely on a single event; also run periodic re-checks (~300–500ms) confirming current foreground package isn't blocked.
- Track `lastRedirectTimestamp` per package; if the same blocked package reappears within 1–2 sec of last redirect, skip re-check and go straight to lock screen (avoid flicker loop).
- If a user rapid-reopens 5+ times in 10 seconds, treat as a UX signal (frustration) rather than just a technical bug — consider a softer "take a break" message instead of repeat-firing the lock screen.

**Service survival / OEM battery optimization:** Accessibility Services persist even if the app's UI task is force-closed, but aggressive OEM battery optimization (notably Xiaomi, Oppo, Vivo — common in the Philippines) can kill background services unless whitelisted. Onboarding must explicitly walk through battery optimization exemption + "autostart" toggles for these OEMs (detect via `Build.MANUFACTURER`).

## Screen flow (designed, mockups already produced)
1. **Onboarding — Welcome**: problem framing, "Get started"
2. **Onboarding — App picker**: checkbox list of installed apps to lock (e.g. TikTok, Mobile Legends)
3. **Onboarding — Study preferences**: session length slider (default ~10–15 min, range 5–30), daily goal preset (Casual/Serious)
4. **Onboarding — Accessibility permission**: guided screen explaining why, deep-links to system settings (step X of 3 indicator)
5. **Onboarding — Battery/autostart permission**: guided screen, OEM-specific note shown conditionally
6. **Onboarding — All set**: confirmation, CTA into first lesson
7. **Lock/redirect screen**: replaces overlay concept — this is now the app's own entry screen shown on redirect. Shows blocked app context, "Start lesson" CTA, no skip option.
8. **Lesson screen**: flashcard flow — character, audio playback, romaji + mnemonic reveal, progress bar, next.
9. **Quiz screen**: one question at a time, multiple choice, immediate visual feedback per tap, progress indicator.
10. **Results screen**: score, credits earned, wallet balance, missed items queued note, primary CTA "Continue to [app]", secondary CTA "Keep studying" (chains into next lesson).
11. **Home dashboard**: wallet balance, streak, tier progress bar, "Study now" quick action.
12. **Settings**: edit locked app list, session length, daily goal, permission health re-check.

Design reference points used: Duolingo's design system (flashcard/quiz pattern, gamified feedback, progress bars) and dedicated Android app-lock UI kits, sourced via Figma Community search — not copied, used as a quality/pattern benchmark.

## Tech stack (decided)
- Kotlin + Jetpack Compose
- Room (SQLite) for all local persistence — cards, SRS progress, wallet, sessions, settings
- Kotlin Coroutines + Flow for async/reactive state
- AccessibilityService + WorkManager for detection and periodic re-checks
- MediaPlayer/ExoPlayer for kana audio playback
- No backend/server for MVP — fully offline
- Firebase Analytics or self-hosted analytics — optional, later, once there are real users
- Min SDK target: API 26 (Android 8.0) — covers most active Filipino Android devices, supports Accessibility Service + WorkManager fully

## Suggested project structure
```
app/
  data/
    Card.kt, UserCardProgress.kt, Session.kt, Wallet.kt   (Room entities)
    AppDatabase.kt
    CardDao.kt, ProgressDao.kt
  srs/
    SrsEngine.kt        (SM-2-lite logic, plain Kotlin, no Android deps — unit testable)
  ui/
    onboarding/
    lesson/
    quiz/
    home/
  service/
    AppLockAccessibilityService.kt
res/
  raw/       (kana audio clips)
  assets/    (seed JSON for card content)
```

## Build milestones (in order)
1. Core data layer — Room entities, seed hiragana JSON, SM-2-lite SRS class (testable independent of UI)
2. Lesson + quiz screens in Compose, wired to real SRS
3. Wallet + credit economy — proportional credits per correct answer, daily soft cap, streaks
4. App detection + redirect — AccessibilityService, back-button override, reopen-race handling
5. Onboarding + permissions flow, including OEM-specific battery/autostart handling
6. Home dashboard + settings
7. Polish pass — empty/error states, permission-revoked banner, app icon/splash

## Explicitly deferred / cut from MVP
- Stroke-order tracing (start with recognition-only quiz types)
- Katakana, vocab, and kanji tiers (hiragana-only launch)
- Social/accountability features (solo loop first)
- Play Store polish, marketing assets (until core loop validated with real testers — classmates suggested as first test group)
- iOS version (Screen Time / Family Controls framework — separate, more restricted implementation)

## Known highest-risk areas to test early, on real devices (not just emulator)
- AccessibilityService reliability across OEM skins common in the Philippines (Xiaomi, Oppo, Vivo)
- The reopen race / redirect debounce logic