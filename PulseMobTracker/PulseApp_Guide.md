# PulseApp Developer Guide

**PulseMobTracker** is an Android application designed to track user digital wellbeing statistics locally on the device. It monitors app usage, screen interactions, and generates daily insights without relying on external servers.

---

## 🏗️ High-Level Architecture

The application follows a **Unidirectional Data Flow** pattern, separated into three clear layers:

1.  **Data Collection Layer (Background)**:
    *   Runs periodically via `WorkManager`.
    *   Polls Android's `UsageStatsManager`.
    *   Stores raw events in a local Room Database.

2.  **Processing Layer (Business Logic)**:
    *   Processes raw events (`OPEN`, `CLOSE`, `SCREEN_ON`, etc.) into meaningful "Sessions" (`AppSession`).
    *   Aggregates sessions into `DailyStats`.
    *   Handles edge cases like "Jitter" (rapid app switching) and Day Boundaries (midnight split).

3.  **Presentation Layer (UI)**:
    *   Built with **Jetpack Compose**.
    *   ViewModels observe `DailyStats` from the Database.
    *   UI is purely reactive; it updates automatically when background workers update the DB.

---

## 🚀 Main Execution Flow

### 1. App Launch (`MainActivity.kt`)
The entry point of the application.
*   **Permission Check**: On `onCreate` and `onResume`, it checks for `OPS_USAGE_STATS` permission.
*   **Initialization**: If permission is granted, it calls `initDataCollection()`.
    *   **Periodic Work**: Enqueues a `PeriodicWorkRequest` for `DataCollectionWorker` (Every 15 mins).
    *   **Immediate Work**: Enqueues a `OneTimeWorkRequest` to sync data immediately.
*   **UI Setup**: Sets up the `MainAppStructure` scaffolding with the bottom navigation bar.

### 2. Background Data Sync (`DataCollectionWorker.kt`)
This is the heartbeat of the app.
*   **Trigger**: Triggered by Android's `WorkManager`.
*   **Action**:
    1.  **Log**: Calls `PulseDataLogger.logUsageStats()` to fetch new system events.
    2.  **Process**: Calls `PulseDataProcessor.processPendingData()` to turn raw events into stats.

---

## 📂 Detailed File Breakdown

### A. Core Logic (Data Collection & Processing)

#### 1. `workers/DataCollectionWorker.kt`
*   **Role**: The bridge between Android system scheduling and our logic.
*   **Key Method**: `doWork()`
    *   Initializes `PulseDatabase`.
    *   Runs the `Logger` first (Fetch), then the `Processor` (Refine).
    *   Returns `Result.success()` or `retry()`.

#### 2. `data_logger/PulseDataLogger.kt`
*   **Role**: The "Miner". It extracts raw data from Android APIs.
*   **Key Logic**:
    *   `logUsageStats()`:
        *   Queries `rawDataDao.getLastEvent()` to find the last sync timestamp.
        *   Calls `usageStatsManager.queryEvents(lastSync, now)`.
        *   Filters events using `PulseEvents` mapping (keeps only OPEN, CLOSE, SCREEN_ON, etc.).
        *   **Batch Insert**: Inserts all new `RawData` entities into the DB.
    *   `collectHistoricalData()`: Runs only if the DB is empty. Fetches the last 7 days of history to populate the app immediately on first install.

#### 3. `data_logger/PulseDataProcessor.kt`
*   **Role**: The "Brain". This is the most complex file containing the core algorithm.
*   **State Management**: Uses bookmarks (`last_processed_app_id` and `last_processed_screen_id`) in `AnalyticsDao` to track progress.
*   **Two-Pass Processing**:
    *   **Pass 1 (App Sessions)**: Matches `APP_OPEN` with `APP_CLOSE`.
        *   **Jitter Handling**: If an app is closed and re-opened within `PULSE_JITTER_THRESHOLD_MS`, it is treated as one continuous session.
        *   **Day Split**: If a session crosses midnight, it splits it into two `AppSession` entries (one for each day).
    *   **Pass 2 (Screen Sessions)**: Matches `SCREEN_ON` with `SCREEN_OFF`.
        *   **Classification**:
            *   `SESSION_GLANCE`: Screen On -> Screen Off (No unlock).
            *   `SESSION_UNLOCK_NOAPP`: Unlock -> Lock (No app opened).
            *   `SESSION_UNLOCK_APP`: Unlock -> App Open (Transition to app usage).
*   **Aggregation (`updateDailyStats`)**:
    *   Calculates `focusScore`, `totalScreenTime`, and `offlineStreak` (excluding sleep hours defined in theme constants).
    *   Updates the `DailyStats` table.

---

### B. Data Management (Database Layer)

#### 1. `data_manager/RawData.kt`
*   **Entity**: `raw_data`
*   **Purpose**: Immutable log of exactly what happened and when.
*   **Key Fields**: `timestamp`, `eventType`, `packageName`, `eventLabel`.

#### 2. `data_manager/AppSession.kt`
*   **Entity**: `app_sessions`
*   **Purpose**: A duration-based record. This is what the UI queries for charts.
*   **Key Fields**: `startTime`, `endTime`, `duration`, `type` (APP, GLANCE, etc.).

#### 3. `data_manager/DailyStats.kt`
*   **Entity**: `daily_stats`
*   **Purpose**: High-level summary for the dashboard.
*   **Key Fields**: `totalScreenTime`, `unlockAppCount`, `focusScore`, `topApp1Package`.
*   **Why**: Calculating these on the fly from millions of `RawData` rows is too slow for UI. We pre-calculate them during the background work.

---

### C. UI Implementation (Feature Layer)

#### 1. `features/Summary/SummaryScreen.kt`
*   **Role**: Dashboard View.
*   **Interaction**:
    *   Allows date navigation (Previous/Next Day).
    *   Displays 4 main cards: `PhoneActivity`, `DeviceAccess`, `OfflineStreak`, `FirstLastApps`.
*   **Reactive**: Uses `LaunchedEffect(processingDate)` to trigger ViewModel re-loads when the user changes the date.

#### 2. `features/Summary/SummaryViewModel.kt`
*   **Role**: Presenter.
*   **Logic**:
    *   Exposes `StateFlow` for each UI Card model.
    *   `loadDataForDate(date)`: Subscribes to `analyticsDao.getDailyStatsFlow(date)`.
    *   **Transformation**: Converts raw primitives from `DailyStats` (e.g., `long` milliseconds) into UI-ready Strings (e.g., "4h 20m"). uses `AppInfoHelper` to resolve package names to App Names.

---

## 📊 Logic Deep Dive: Session Classification

The `PulseDataProcessor` uses a priority based classification:
1.  **Did the screen turn on?** -> Start monitoring.
2.  **Did the user Unlock?**
    *   **No**: It's a `GLANCE`.
    *   **Yes**: Continue monitoring.
3.  **Did an App Open?**
    *   **No** (User locked phone): It's `UNLOCK_NOAPP` (e.g., checked notifications).
    *   **Yes**: It's `UNLOCK_APP`. The "System" session ends, and an `SESSION_APP` begins.

## 🛠 Project Structure Mapping

```text
com.focux.pulse
├── MainActivity.kt          // Entry Point
├── workers/                 // Background Jobs
│   └── DataCollectionWorker.kt
├── data_logger/             // Logic Core
│   ├── PulseDataLogger.kt   // Data Fetching
│   └── PulseDataProcessor.kt// Data Processing
├── data_manager/            // Room Database
│   ├── PulseDatabase.kt
│   ├── Entities (RawData, AppSession, DailyStats)
│   └── DAOs
├── features/                // UI Screens
│   ├── Summary/
│   ├── Timeline/
│   ├── Insights/
│   └── Configuration/
└── ui/                      // Shared Components
    └── theme/               // Colors, Type, Constants
```
