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
    val penaltyMultiplier: Int = 3, // 3x multiplier when daily limit exceeded
    val exemptionWindowSecs: Int = 10, // Seconds to bypass double speedbump
    val doomScrollWindowSecs: Int = 30, // sliding window for doom scroll detection
    val doomScrollThreshold: Int = 5  // number of switches within the window to trigger
)

class MindfulLimitsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = PulseDatabase.getDatabase(application)
    private val appInfoDao = database.appInfoDao()
    private val analyticsDao = database.analyticsDao()
    // Pre-calculate lists to avoid recalculating on every emission if possible
    // (For now doing it inline in the combine bloc due to fast list sizes)

    init {
        // Automatically seed the DB with all install apps (with launcher intents) 
        // so they appear in this configuration list even if the user hasn't opened them yet.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pm = application.packageManager
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                
                val appInfos = resolveInfos.mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    if (packageName == application.packageName) return@mapNotNull null
                    
                    val label = resolveInfo.loadLabel(pm).toString()
                    com.focux.pulse.data.local.entities.AppInfo(
                        packageName = packageName,
                        appName = label,
                        category = com.focux.pulse.data.local.entities.AppCategory.NEUTRAL
                    )
                }
                
                appInfoDao.insertAllIfNotExists(appInfos)
            } catch (e: Exception) {
                com.focux.pulse.utilities.Logger.e("MindfulLimitsViewModel", "Failed to seed installed apps", e)
            }
        }
    }

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

                breathingDuration = parse(analyticsDao.getState("mindful_base_duration")) ?: 4,
                penaltyMultiplier = parse(analyticsDao.getState("mindful_penalty_multiplier")) ?: 3,
                exemptionWindowSecs = parse(analyticsDao.getState("mindful_exemption_window_secs")) ?: 10,
                doomScrollWindowSecs = parse(analyticsDao.getState("mindful_doomscroll_window_secs")) ?: 30,
                doomScrollThreshold = parse(analyticsDao.getState("mindful_doomscroll_threshold")) ?: 5
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
    
    fun updateMindfulInterventions(breathing: Int, penalty: Int, exemption: Int, doomWindowSecs: Int, doomThreshold: Int) {
        viewModelScope.launch {
            analyticsDao.updateState(SystemState("mindful_base_duration", breathing.toString()))
            analyticsDao.updateState(SystemState("mindful_penalty_multiplier", penalty.toString()))
            analyticsDao.updateState(SystemState("mindful_exemption_window_secs", exemption.toString()))
            analyticsDao.updateState(SystemState("mindful_doomscroll_window_secs", doomWindowSecs.toString()))
            analyticsDao.updateState(SystemState("mindful_doomscroll_threshold", doomThreshold.toString()))
            loadGlobalLimits()
        }
    }
}
