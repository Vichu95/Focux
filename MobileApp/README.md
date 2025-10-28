Here’s a crisp, shared doc you can keep in your repo’s `/docs/` as `Focux_Phase1_Android.md`.

# Focux — Personal Usage Tracker

## Phase 1: Android (PhoneUsageTracker)

## 1) What is it for?

A **silent background tracker** that records how the phone is used (apps, screen, unlocks, notifications) to create a **clean dataset** for later analysis and dashboards on laptop/web. No nudges, no UI—**logging only**.

## 2) Goals (Expected Outcomes)

* **Reliable capture** of core usage signals (app foreground time, screen on/off, unlocks, notifications posted).
* **Local storage** (file/Room DB) with timestamps and package names.
* **Low-friction**: runs after install and across reboots.
* **Exportable data** (CSV/JSON) for later analysis on laptop.
* **Small, testable milestones** with clear acceptance checks.

## 3) Non-Goals (Phase 1)

* No visualizations on phone.
* No cloud sync.
* No behavioral feedback loops/neuroscience layer (later).
* No intrusive overlays or user-interrupting UI.

## 4) Constraints & Tech

* **Min SDK 23**, Target 35; **Kotlin**, **Jetpack Compose**, **Material 3**.
* Background-friendly; battery-aware.
* Permissions required:

  * `PACKAGE_USAGE_STATS` (user grants in Settings)
  * `RECEIVE_BOOT_COMPLETED`
  * `FOREGROUND_SERVICE`
  * `POST_NOTIFICATIONS` (for NotificationListener registration; no user-facing notifications needed now)

## 5) Signals to Track (Phase 1 scope)

* **App usage**: foreground package + enter/exit events; daily time per app.
* **Screen**: `SCREEN_ON`, `SCREEN_OFF`.
* **Unlocks**: `USER_PRESENT`.
* **Notifications**: app posting notifications (package, post time).
* (Optional later) **Touches/Swipes** via AccessibilityService (privacy-heavy; postpone).

## 6) Data Model (initial)

**Event table (or newline-JSON / CSV in filesDir):**

* `ts` (epoch ms, UTC)
* `type` (`SCREEN_ON|SCREEN_OFF|UNLOCK|APP_MOVE_TO_FOREGROUND|APP_MOVE_TO_BACKGROUND|NOTIF_POSTED`)
* `package` (nullable; present for app/notif events)
* `extra` (JSON string; e.g., notification id/title hash if captured)
* `session_id` (nullable; for grouping later)

**Daily aggregation (derived later):**

* `date`, `package`, `totalForegroundMs`, `openCount`

## 7) Milestones (tiny steps, each shippable)

### M1 — Screen & Unlock Logger

**What:** Log `SCREEN_ON`, `SCREEN_OFF`, `UNLOCKED` to `usage_log.txt`.
**How:** `BroadcastReceiver` for `ACTION_SCREEN_ON/OFF/USER_PRESENT`.
**Accept:** Lock/unlock a few times → file has dated lines with those states.

### M2 — Boot Persistence

**What:** Start minimal tracking after reboot.
**How:** `RECEIVE_BOOT_COMPLETED` → start a lightweight service or re-register receiver.
**Accept:** Reboot device → perform screen on/off/unlock → events appear in log.

### M3 — Usage Access Check + Settings Deep Link

**What:** Detect if `Usage Access` is granted; show single button to open Settings.
**How:** Small Compose screen; `AppOpsManager`/Settings intent.
**Accept:** Without access → button opens correct settings; with access → shows “Ready”.

### M4 — Foreground App Events (raw)

**What:** Record `MOVE_TO_FOREGROUND/BACKGROUND` via `UsageStatsManager.queryEvents`.
**How:** Periodic (e.g., 10s) polling in a foreground service; append to file.
**Accept:** Open 2–3 apps → log shows ordered app enter/exit events with packages.

### M5 — Per-App Session Stitching

**What:** Convert raw events into sessions with start/end/duration (in-memory + write).
**How:** Simple sessionizer: last FG → BG/next FG boundary.
**Accept:** Short multi-app usage → `sessions.txt` (or DB) lists sessions with `package`, `start`, `end`, `durationMs`.

### M6 — Notification Posts

**What:** Capture posted notifications’ package and timestamp.
**How:** `NotificationListenerService`.
**Accept:** Receive a few notifications → `notifications.txt` contains lines with package + timestamp.

### M7 — Room DB (optional but recommended)

**What:** Move from text files to `Room` for reliability.
**How:** `events` and `sessions` tables; DAO insertions.
**Accept:** Insert/read tested; app still logs all signals; simple count query works.

### M8 — Daily Aggregation Job

**What:** Nightly (or on-demand) aggregation per app/day.
**How:** `WorkManager` job; read sessions → write `daily_usage` table/file.
**Accept:** After a day, `daily_usage` shows totals per package.

### M9 — Export (CSV/JSON)

**What:** Export `events/sessions/daily_usage` to `/Download/Focux/` (or share sheet).
**How:** Scoped storage APIs; write CSV/JSON.
**Accept:** File visible in Files app; readable on laptop.

> You can stop at **M5** and already have useful raw data. M6–M9 add completeness.

## 8) Minimal UI (Compose)

* Single screen with:

  * Usage Access status (Granted / Not granted) + **Open Settings** button.
  * Tracking status (On/Off) + **Start/Stop** toggle.
  * **Export** button (after M9).
* No charts or lists in Phase 1.

## 9) Privacy & Security (baseline)

* Store **locally only**; no network I/O in Phase 1.
* Avoid capturing notification content; log only **package + timestamp**.
* No AccessibilityService until explicitly enabled in a later phase.
* Add a simple **“Delete all data”** action in settings later.

## 10) Testing Checklist (per milestone)

* Device: real phone, normal usage for 10–30 min.
* Verify:

  * Files/DB rows grow as actions happen.
  * Timestamps are monotonic and timezone-agnostic (store UTC).
  * After reboot, logging resumes (M2).
  * No crashes or ANRs in Logcat.
  * Battery impact: <1%/hr during light use (rough heuristic for polling interval).

## 11) Risks & Mitigations

* **Usage access denied** → clear UI prompt + deep link (M3).
* **OEM background limits** → run as **foreground service** with minimal ongoing notification; or use WorkManager where possible.
* **Battery** → increase polling interval; prefer event stitching over frequent queries.

## 12) Phase 2+ (Outlook)

* **Desktop tracker** (Windows/macOS): app/process focus, active window titles, website domains.
* **Unified export format** + **cross-device merge**.
* Dashboards (Python/Streamlit or web).
* Attention/dopamine heuristics & feedback loops (nudges opt-in).

---

### Implementation Order (quick copy list)

`M1 → M2 → M3 → M4 → M5 → (M6) → (M7) → (M8) → (M9)`

If you want, I can now generate the **exact code for M1** (receiver + manifest edits + a tiny Compose screen with a “View Log” button) so you can paste and run immediately.
