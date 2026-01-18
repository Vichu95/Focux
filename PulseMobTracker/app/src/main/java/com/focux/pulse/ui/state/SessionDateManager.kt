package com.focux.pulse.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * Singleton to manage the currently selected date across the app session.
 * 
 * Requirements:
 * 1. Syncs date between Summary and Timeline screens.
 * 2. Persists while app is in background or switching tabs.
 * 3. Resets to "Today" only when app is fully closed and restarted (Process death),
 *    which is the natural behavior of a Kotlin object singleton in Android.
 */
object SessionDateManager {
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    fun setDate(date: LocalDate) {
        _selectedDate.value = date
    }
}
