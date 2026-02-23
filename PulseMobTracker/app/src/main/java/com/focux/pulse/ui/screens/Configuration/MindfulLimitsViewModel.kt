package com.focux.pulse.ui.screens.Configuration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.data.local.entities.AppInfo
import com.focux.pulse.data.local.entities.SystemState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MindfulLimitsUiState(
    val apps: List<AppInfo> = emptyList(),
    val isEditMode: Boolean = false,
    val selectedFilters: Set<String> = setOf("Distracting", "Productive", "Neutral"),
    val searchQuery: String = "",
    
    // Global defaults
    val distSession: Int? = 5,
    val distDaily: Int? = 30,
    val distOpens: Int? = 10,
    
    val prodSession: Int? = null,
    val prodDaily: Int? = null,
    val prodOpens: Int? = null,
    
    val neutSession: Int? = null,
    val neutDaily: Int? = null,
    val neutOpens: Int? = null,
    
    val breathingDuration: Int = 4, // 4s per phase
    val doomScrollWindowSecs: Int = 30, // sliding window for doom scroll detection
    val doomScrollThreshold: Int = 4  // number of switches within the window to trigger
)

class MindfulLimitsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = PulseDatabase.getDatabase(application)
    private val appInfoDao = database.appInfoDao()
    private val analyticsDao = database.analyticsDao()
    private val _isEditMode = MutableStateFlow(false)
    private val _selectedFilters = MutableStateFlow(setOf("Distracting", "Productive", "Neutral"))
    private val _searchQuery = MutableStateFlow("")
    private val _globalLimits = MutableStateFlow(MindfulLimitsUiState())

    init {
        loadGlobalLimits()
    }

    private fun loadGlobalLimits() {
        viewModelScope.launch {
            fun parse(s: String?): Int? = s?.takeIf { it.isNotBlank() }?.toIntOrNull()
            
            _globalLimits.value = _globalLimits.value.copy(
                distSession = parse(analyticsDao.getState("limit_distracting_session")) ?: 5,
                distDaily = parse(analyticsDao.getState("limit_distracting_daily")) ?: 30,
                distOpens = parse(analyticsDao.getState("limit_distracting_opens")) ?: 10,

                prodSession = parse(analyticsDao.getState("limit_productive_session")),
                prodDaily = parse(analyticsDao.getState("limit_productive_daily")),
                prodOpens = parse(analyticsDao.getState("limit_productive_opens")),

                neutSession = parse(analyticsDao.getState("limit_neutral_session")),
                neutDaily = parse(analyticsDao.getState("limit_neutral_daily")),
                neutOpens = parse(analyticsDao.getState("limit_neutral_opens")),

                breathingDuration = parse(analyticsDao.getState("limit_breathing_duration")) ?: 4,
                doomScrollWindowSecs = parse(analyticsDao.getState("limit_doomscroll_window_secs")) ?: 30,
                doomScrollThreshold = parse(analyticsDao.getState("limit_doomscroll_threshold")) ?: 4
            )
        }
    }

    val uiState: StateFlow<MindfulLimitsUiState> = combine(
        appInfoDao.getAllAppsFlow(),
        _isEditMode,
        _selectedFilters,
        _searchQuery,
        _globalLimits
    ) { apps, editMode, filters, query, globals ->
        var filteredApps = apps.filter { app -> filters.any { app.category.equals(it, ignoreCase = true) } }
        
        if (query.isNotBlank()) {
            filteredApps = filteredApps.filter { 
                it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
        }
        
        globals.copy(
            apps = filteredApps,
            isEditMode = editMode,
            selectedFilters = filters,
            searchQuery = query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MindfulLimitsUiState())

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }
    
    fun toggleFilter(filter: String) {
        val current = _selectedFilters.value
        if (filter in current) {
            _selectedFilters.value = current - filter
        } else {
            _selectedFilters.value = current + filter
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateAppLimits(packageName: String, session: Int?, daily: Int?, opens: Int?) {
        viewModelScope.launch {
            appInfoDao.updateAppLimits(packageName, session, daily, opens)
        }
    }

    fun updateGlobalLimits(category: String, session: Int?, daily: Int?, opens: Int?) {
        viewModelScope.launch {
            val prefix = "limit_${category.lowercase()}"
            suspend fun save(key: String, value: Int?) {
                if (value == null) {
                    analyticsDao.updateState(SystemState("${prefix}_$key", ""))
                } else {
                    analyticsDao.updateState(SystemState("${prefix}_$key", value.toString()))
                }
            }
            save("session", session)
            save("daily", daily)
            save("opens", opens)
            loadGlobalLimits()
        }
    }
    
    fun updateBreathingDuration(duration: Int) {
        viewModelScope.launch {
            analyticsDao.updateState(SystemState("limit_breathing_duration", duration.toString()))
            loadGlobalLimits()
        }
    }

    fun updateDoomScrollLimits(windowSecs: Int, threshold: Int) {
        viewModelScope.launch {
            analyticsDao.updateState(SystemState("limit_doomscroll_window_secs", windowSecs.toString()))
            analyticsDao.updateState(SystemState("limit_doomscroll_threshold", threshold.toString()))
            loadGlobalLimits()
        }
    }
}
