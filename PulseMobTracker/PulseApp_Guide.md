# PulseApp Developer Guide

**PulseMobTracker** is an Android application designed to track user digital wellbeing statistics locally on the device. It monitors app usage, screen interactions, and generates daily insights without relying on external servers.

---

## 🏗️ High-Level Architecture

The application follows a **Unidirectional Data Flow** pattern, separated into three clear layers:

1.  **Data Layer (`data/`)**:
    *   **Source**: Polls Android's `UsageStatsManager` (`SystemUsageSource`).
    *   **Processing**: Converts raw events into sessions and stats (`data/processors`).
    *   **Storage**: Local Room Database (`data/local`).
    *   **Workers**: Background scheduling (`data/workers`).

2.  **Presentation Layer (`ui/`)**:
    *   Built with **Jetpack Compose**.
    *   Organized by feature screens (`ui/screens`).
    *   ViewModels observe data derived from `DailyStats` and `AppSession` tables.

3.  **Utilities Layer (`utilities/`)**:
    *   Helper classes for formatting and package management.

---

## 🚀 Main Execution Flow

### 1. App Launch (`MainActivity.kt`)
The entry point of the application.
*   **Permission Check**: On `onCreate` and `onResume`, it checks for `OPS_USAGE_STATS` permission.
*   **Initialization**: If permission is granted, it requests `initDataCollection()`.
    *   **Periodic Work**: Enqueues a `PeriodicWorkRequest` for `DataCollectionWorker` (Every 15 mins).
    *   **Immediate Work**: Enqueues a `OneTimeWorkRequest` to sync data immediately.
*   **Routing**: Displays `PermissionScreen` (if needed) or the main `Scaffold` with `BottomNavBar` and feature screens.

### 2. Background Data Sync (`DataCollectionWorker.kt`)
This is the heartbeat of the app.
*   **Trigger**: Triggered by Android's `WorkManager`.
*   **Action**:
    1.  **Log**: Calls `SystemUsageSource.logUsageStats()` to fetch new system events.
    2.  **Process**: Calls `SessionProcessor.processPendingData()` to turn raw events into stats.

---

## 📂 Detailed File Breakdown

### A. Data Layer (`com.focux.pulse.data`)

#### 1. `workers/DataCollectionWorker.kt`
*   **Role**: The bridge between Android system scheduling and our logic.
*   **Key Method**: `doWork()`
    *   Initializes `PulseDatabase`.
    *   Runs the Source to fetch, then the Processor to refine.

#### 2. `source/SystemUsageSource.kt`
*   **Role**: The "Miner". It extracts raw data from Android APIs.
*   **Key Logic**:
    *   `logUsageStats()`:
        *   Queries `rawDataDao.getLastEvent()` to find the last sync timestamp.
        *   Calls `usageStatsManager.queryEvents(lastSync, now)`.
        *   **Batch Insert**: Inserts all new `RawData` entities into the DB.
    *   `collectHistoricalData()`: Fetches last 7 days of history on first install.

#### 3. `processors/SessionProcessor.kt` (Orchestrator)
*   **Role**: The "Brain". Orchestrates the pipeline data flow.
*   **Logic**: It delegates work to specialized sub-processors:
    1.  **`AppSessionProcessor`**: Processes raw events into App Sessions.
    2.  **`ScreenSessionProcessor`**: Processes screen cycles into Unlock/Glance sessions.
    3.  **`DailySummaryProcessor`**: Aggregates sessions into daily statistics.

#### 4. `processors/AppSessionProcessor.kt`
*   **Role**: Handles App Usage logic.
*   **Pass 1**: Matches `APP_OPEN` with `APP_CLOSE` to create `SESSION_APP`.
    *   **Jitter Handling**: Ignores rapid close/re-open sequences (< threshold).
    *   **Day Split**: Uses `SessionSplitter` to handle midnight boundaries.

#### 5. `processors/ScreenSessionProcessor.kt`
*   **Role**: Handles Screen Interaction logic.
*   **Pass 2**: Matches `SCREEN_ON` with `SCREEN_OFF`.
    *   **`SESSION_GLANCE`**: Screen On -> Screen Off (No unlock).
    *   **`SESSION_UNLOCK_NOAPP`**: Unlock -> Lock (No app opened).
    *   **`SESSION_UNLOCK_APP`**: Unlock -> App Open (Transition to app usage).

#### 6. `processors/DailySummaryProcessor.kt`
*   **Role**: Aggregator.
*   **Logic**:
    *   Calculates `totalScreenTime`, `focusScore`, etc.
    *   **Offline Streak**: Detects longest gap in usage (ignoring sleep hours).
    *   **App Registration**: Auto-discovers new apps and adds them to `AppInfo` table.

#### 7. `processors/SessionSplitter.kt`
*   **Role**: Shared Utility.
*   **Logic**: Splits session time ranges at day boundaries (e.g., if a session runs from 11:50 PM to 12:10 AM, it splits it into two sessions).

### B. Local Storage (`data/local`)

*   **`PulseDatabase.kt`**: Room database definition.
*   **`entities/`**:
    *   `RawData`: Immutable log of system events.
    *   `AppSession`: Processed duration-based record.
    *   `DailyStats`: High-level summary for dashboards.
*   **`dao/`**: Data Access Objects for database queries.

---

### C. UI Implementation (`com.focux.pulse.ui`)

#### 1. `screens/`
*   **Summary**: Dashboard view (`PhoneActivity`, `DeviceAccess`, `OfflineStreak`).
*   **Timeline**: Detailed list of detailed sessions.
*   **Insights**: Weekly trends and analysis charts.
*   **Configuration**: User settings.
*   **onboarding**: `PermissionScreen` for first run.
*   **components**: Shared UI parts like `BottomNavBar` and `AppIcon`.

#### 2. `theme/`
*   Centralized styling (colors, type, dimensions, and constants).

---

## 🛠 Project Structure Mapping

```text
com.focux.pulse
├── data
│   ├── local                 // Database (Room)
│   │   ├── dao
│   │   ├── entities
│   │   └── PulseDatabase.kt
│   ├── processors            // Business Logic
│   │   ├── SessionProcessor.kt (Orchestrator)
│   │   ├── AppSessionProcessor.kt
│   │   ├── ScreenSessionProcessor.kt
│   │   ├── DailySummaryProcessor.kt
│   │   └── SessionSplitter.kt
│   ├── source                // Data Fetching
│   │   └── SystemUsageSource.kt
│   └── workers               // Background Jobs
│       └── DataCollectionWorker.kt
├── ui
│   ├── screens               // UI Features
│   │   ├── Summary
│   │   ├── Timeline
│   │   ├── Insights
│   │   ├── Configuration
│   │   ├── onboarding
│   │   └── components
│   └── theme                 // Theme & Styles
└── utilities
    ├── AppInfoHelper.kt
    └── dummyStub.kt
```
