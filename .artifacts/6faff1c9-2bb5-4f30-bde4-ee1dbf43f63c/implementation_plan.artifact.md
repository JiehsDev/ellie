# Weighted Locked App Categories Implementation Plan

Implement weighted restriction tiers for locked apps: **Light** (gentle reminder every 3 minutes), **Average** (soft block / time-capped interrupt every 3 minutes), and **Extreme** (immediate full redirect, current behavior).

## User Review Required

> [!IMPORTANT]
> This feature introduces a new `tier` property to `LockedApp` requiring a Room database migration (version 5 to 6). Existing locked apps will default to `EXTREME` (strict locking) to preserve backward compatibility.

## Proposed Changes

### Data Layer
#### [MODIFY] [LockedApp.kt](file:///C:/Users/abell/AndroidStudioProjects/Jikan/app/src/main/java/com/example/jikan/data/LockedApp.kt)
- Add `LockTier` enum (`LIGHT`, `AVERAGE`, `EXTREME`).
- Add `tier: LockTier` property to `LockedApp` entity (defaulting to `EXTREME`).

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/abell/AndroidStudioProjects/Jikan/app/src/main/java/com/example/jikan/data/AppDatabase.kt)
- Create `MIGRATION_5_6` adding the `tier` column to the `locked_apps` table.
- Bump database version to 6 and register `MIGRATION_5_6`.

### Service & Logic Layer
#### [MODIFY] [AppLockAccessibilityService.kt](file:///C:/Users/abell/AndroidStudioProjects/Jikan/app/src/main/java/com/example/jikan/service/AppLockAccessibilityService.kt)
- Track locked apps with their tiers.
- When wallet balance is 0 and a locked app is in the foreground:
  - **EXTREME**: Immediate redirect to `LockActivity`.
  - **AVERAGE**: Allow briefly or trigger periodic soft reminder / interrupt every 3 minutes.
  - **LIGHT**: Show a gentle notification/toast reminder every 3 minutes without blocking.

### UI Layer
#### [MODIFY] [LockedAppsScreen.kt](file:///C:/Users/abell/AndroidStudioProjects/Jikan/app/src/main/java/com/example/jikan/ui/settings/LockedAppsScreen.kt)
- Add tier selection UI (chips or selector for Light, Average, Extreme) for each locked app.

## Verification Plan

### Automated Tests
- Run Gradle build (`app:assembleDebug`) to verify compilation and database migration correctness.

### Manual Verification
- Deploy to device/emulator, set app tiers, test behavior when wallet balance reaches 0.
