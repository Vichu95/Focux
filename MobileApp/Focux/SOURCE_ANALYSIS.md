# Comprehensive Source Code Analysis

**Date:** 2025-12-01
**Scope:** All Source Files in `app/src/main/java`

## 1. Services Analysis

| Service Name | Type | Purpose | Risk Assessment |
| :--- | :--- | :--- | :--- |
| **`GlobalTouchService`** | `AccessibilityService` | Listens for global user interactions (scrolls, clicks, text entry). | **CRITICAL**. Currently performs keylogging and excessive passive logging. |
| **`EventListenerService`** | `ForegroundService` | Runs in background to poll `UsageStatsManager` and listen for Screen events. | **MEDIUM**. Requires a valid persistent notification (implemented). Safe if `UsageStats` permission is granted. |

## 2. Permissions Analysis

| Permission | Source | Usage | Status |
| :--- | :--- | :--- | :--- |
| `BIND_ACCESSIBILITY_SERVICE` | Manifest | Used by `GlobalTouchService` to intercept UI events. | **HIGH RISK**. Must provide user benefit (intervention). |
| `PACKAGE_USAGE_STATS` | Manifest | Used by `EventListenerService` to query app usage history. | **SAFE**. Standard for digital wellbeing apps. |
| `FOREGROUND_SERVICE` | Manifest | Used by `EventListenerService` to keep the process alive. | **SAFE**. |
| `RECEIVE_BOOT_COMPLETED` | Manifest | Used by `BootReceiver` to restart tracking on reboot. | **SAFE**. |
| `POST_NOTIFICATIONS` | `StarterActivity` | Requesting permission to show the foreground service notification. | **SAFE**. |

## 3. File-by-File Logging Analysis

### A. `GlobalTouchService.kt`
**Role:** The primary "spyware-like" component.
**Logged Events (to DB):**
*   `TYPE_VIEW_TEXT_CHANGED` -> **KEYLOGGING** (Captures text entry).
*   `TYPE_VIEW_CLICKED` -> Logs every tap.
*   `TYPE_VIEW_SCROLLED` -> Logs scroll direction (Up/Down/Left/Right).
*   `TYPE_WINDOW_STATE_CHANGED` -> Logs app switching/navigation.
*   `TYPE_VIEW_FOCUSED` -> Logs UI element focus.
*   `SERVICE_LIFECYCLE` -> Logs Connected/Interrupted states.

### B. `EventListenerService.kt`
**Role:** Background poller and manager.
**Logged Events (to DB):**
*   `USAGE_EVENT` -> Polls `UsageStatsManager` for:
    *   `ACTIVITY_RESUMED` / `PAUSED` / `STOPPED`
    *   `USER_INTERACTION`
    *   `SCREEN_INTERACTIVE` / `NON_INTERACTIVE`
    *   `KEYGUARD_SHOWN` / `HIDDEN`
*   `SERVICE_LIFECYCLE` -> Logs creation, start commands, and destruction.

### C. `ScreenReceiver.kt`
**Role:** BroadcastReceiver for screen state.
**Logged Events (to DB):**
*   `SCREEN_EVENT` -> `SCREEN_ON`
*   `SCREEN_EVENT` -> `SCREEN_OFF`
*   `SCREEN_EVENT` -> `UNLOCKED` (User Present)

### D. `BootReceiver.kt`
**Role:** Restarts services on device boot.
**Logged Events (to DB):**
*   `SYSTEM_EVENT` -> `BOOT_RECEIVER_TRIGGERED`
*   `SYSTEM_EVENT` -> `BOOT_ACTION_RECEIVED`
*   `SYSTEM_EVENT` -> `BOOT_SERVICE_STARTING` / `STARTED_OK` / `FAILED`

### E. `StarterActivity.kt`
**Role:** Permission request and initial service start.
**Logged Events (to DB):**
*   `STARTER_LIFECYCLE` -> `POST_NOTIFICATIONS_DENIED`
*   `STARTER_LIFECYCLE` -> `NOTIF_TOGGLE_DISABLED`
*   `STARTER_LIFECYCLE` -> `SERVICE_REQUESTED_WITH_NOTIF`

### F. `MainActivity.kt`
**Role:** Dashboard UI and Database Management.
**Logged Events:**
*   **None directly**. It *reads* from the DB to display stats.
*   Triggers `db.logEventDao().clearAndReset()` (Deletes data).

## 4. Summary of Data Collected
The application currently constructs a near-complete timeline of user activity by combining:
1.  **Physical Interactions**: Taps, Scrolls, Text Input (via Accessibility).
2.  **App Usage**: Which apps are open and for how long (via UsageStats).
3.  **Device State**: Screen On/Off, Unlock, Boot (via Receivers).

**Conclusion:** The combination of `GlobalTouchService` (granular input) and `EventListenerService` (app usage) creates a redundant and highly invasive data profile. The `GlobalTouchService` logging MUST be removed to comply with policies, while `EventListenerService` (UsageStats) is generally acceptable for this category of app.
