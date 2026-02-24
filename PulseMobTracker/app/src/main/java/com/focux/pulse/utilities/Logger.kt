package com.focux.pulse.utilities

import android.util.Log
import com.focux.pulse.BuildConfig

/**
 * A simple logger utility that wraps android.util.Log.
 * 
 * Ensures that debug and error logs are only printed to Logcat if the app is
 * running in a DEBUG build, preventing log spam and information leakage in production.
 */
object Logger {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg)
        }
    }

    fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, msg)
        }
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (t != null) {
                Log.e(tag, msg, t)
            } else {
                Log.e(tag, msg)
            }
        }
    }
}
