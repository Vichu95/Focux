# Focux App - Privacy & Policy Audit Report

**Date:** 2025-12-01
**Auditor:** Senior Android Architect & Policy Expert

## 1. Executive Summary
The current implementation of "Focux" is at **High Risk** of rejection or suspension by the Google Play Store due to violations of the **User Data Policy** and **Accessibility API Policy**. The app currently functions as a passive activity logger (spyware behavior) rather than a productivity tool. Immediate refactoring is required to remove keylogging features and shift to a privacy-first, RAM-only architecture.

---

## 2. Data Tracking Audit
The following table details what data is currently being captured and stored.

| Data Point | Source Event | Storage | Risk Level | Status | Developer Comments |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Keystrokes / Text Input** | `TYPE_VIEW_TEXT_CHANGED` | Local DB (`LogEvent`) | **CRITICAL** | **MUST REMOVE** (Keylogging) | This is required to know if person is typing something. Just for knowing scrolling or actual usage. No need to know what text, just if typing. |
| **App Navigation** | `TYPE_WINDOW_STATE_CHANGED` | Local DB | High | **Refactor** (Use `UsageStatsManager`) | Needed to know if application is minimized or opened etc |
| **Scroll Direction** | `TYPE_VIEW_SCROLLED` | Local DB | High | **Refactor** (RAM-Only Buffer) | To know if person is scrolling, how fast scrolled, etc |
| **Clicks/Taps** | `TYPE_VIEW_CLICKED` | Local DB | High | **Refactor** (RAM-Only if needed) | Again to analyze if person actually uses the app, or just opens and closes |
| **Package Names** | `event.packageName` | Local DB | Medium | **Refactor** (Use `UsageStatsManager`) | To know which app is being opened |
| **Timestamps** | `System.currentTimeMillis()` | Local DB | Low | Safe | To know speed of scrolling, etc |

> [!WARNING]
> **Keylogging Detected**: The app listens for `TYPE_VIEW_TEXT_CHANGED`. This allows the app to potentially record passwords, private messages, and credit card numbers. This is an immediate policy violation.

---

## 3. Service & Permission Analysis

### A. Permissions (`AndroidManifest.xml`)

| Permission | Status | Analysis |
| :--- | :--- | :--- |
| `BIND_ACCESSIBILITY_SERVICE` | 🔴 **RED** | **Current**: Used for passive logging. **Required**: Must be used for user-facing features (e.g., "Nudge" intervention). |
| `PACKAGE_USAGE_STATS` | 🟢 **GREEN** | Standard permission for Digital Wellbeing apps. Safe to use. |
| `FOREGROUND_SERVICE` | 🟡 **YELLOW** | Requires valid notification and user-perceivable task. Ensure the notification is clear. |
| `RECEIVE_BOOT_COMPLETED` | 🟢 **GREEN** | Standard for restarting services. Safe. |

### B. Services

#### 1. `GlobalTouchService` (AccessibilityService)
*   **Problem**: Currently dumps all events into `LogWriter`.
*   **Verdict**: **Unsafe**.
*   **Fix**: Remove `LogWriter` calls. Implement `ScrollVelocityDetector` in memory.

#### 2. `LogWriter` & `AppDatabase`
*   **Problem**: Persistently stores granular user interactions.
*   **Verdict**: **Unsafe** for Accessibility data.
*   **Fix**: Only use Database for high-level stats (e.g., "Total Focus Time") derived from `UsageStatsManager`, NOT raw events.

---

## 4. Recommendations & Roadmap

### Phase 1: Immediate Cleanup (Privacy Fixes)
1.  **Delete Keylogging**: Remove `TYPE_VIEW_TEXT_CHANGED` from `GlobalTouchService`.
2.  **Stop DB Logging**: Remove `LogWriter.append()` calls from `onAccessibilityEvent`.
3.  **RAM-Only Logic**: Implement a circular buffer to calculate scroll velocity in real-time without storage.

### Phase 2: Feature Implementation (Policy Compliance)
1.  **Intervention**: Implement a "Doom Scroll Nudge" (Toast or Overlay) that triggers when velocity exceeds a threshold. This provides the "User Benefit" required for Accessibility Services.
2.  **Prominent Disclosure**: Add a screen before the permission request:
    > "Focux uses the Accessibility Service to detect rapid scrolling patterns (Doom Scrolling) in real-time. This data is processed instantly on your device and is NEVER stored or shared. We do not read your text or content."

### Phase 3: Safe Analytics
1.  **Use UsageStatsManager**: For tracking "Time Spent in App" and "App Launch Counts", use the standard Android `UsageStatsManager` API. It is battery-efficient and privacy-safe.

---

## 5. Conclusion
The app in its current state mimics spyware. By pivoting to **RAM-only processing** for the "Doom Scroll" feature and using **UsageStatsManager** for general analytics, you can achieve your product goals while remaining fully compliant with Google Play Policies.
