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
    
    // Master Toggles
    val isMasterEnabled: Boolean = true,
    val isAppLimitsEnabled: Boolean = true,
    val isDoomScrollEnabled: Boolean = true,
    
    // Global defaults
    val distSession: Int = com.focux.pulse.utilities.NO_LIMIT,
    val distDaily: Int = com.focux.pulse.utilities.NO_LIMIT,
    val distOpens: Int = com.focux.pulse.utilities.NO_LIMIT,
    
    val prodSession: Int = com.focux.pulse.utilities.NO_LIMIT,
    val prodDaily: Int = com.focux.pulse.utilities.NO_LIMIT,
    val prodOpens: Int = com.focux.pulse.utilities.NO_LIMIT,
    
    val neutSession: Int = com.focux.pulse.utilities.NO_LIMIT,
    val neutDaily: Int = com.focux.pulse.utilities.NO_LIMIT,
    val neutOpens: Int = com.focux.pulse.utilities.NO_LIMIT,
    
    val breathingDuration: Int = 0,
    val penaltyMultiplier: Int = 0,
    val exemptionWindowSecs: Int = 0,
    val doomScrollWindowSecs: Int = 0,
    val doomScrollThreshold: Int = 0
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
            fun parse(s: String?): Int {
                val value = s?.takeIf { it.isNotBlank() }?.toIntOrNull()
                return value ?: com.focux.pulse.utilities.NO_LIMIT
            }
            
            _globalLimits.value = _globalLimits.value.copy(
                isMasterEnabled = analyticsDao.getState("pulse_master_enabled")?.toBoolean() ?: true,
                isAppLimitsEnabled = analyticsDao.getState("pulse_app_limits_enabled")?.toBoolean() ?: true,
                isDoomScrollEnabled = analyticsDao.getState("pulse_doomscroll_enabled")?.toBoolean() ?: true,
                
                distSession = parse(analyticsDao.getState("limit_distracting_session")),
                distDaily = parse(analyticsDao.getState("limit_distracting_daily")),
                distOpens = parse(analyticsDao.getState("limit_distracting_opens")),

                prodSession = parse(analyticsDao.getState("limit_productive_session")),
                prodDaily = parse(analyticsDao.getState("limit_productive_daily")),
                prodOpens = parse(analyticsDao.getState("limit_productive_opens")),

                neutSession = parse(analyticsDao.getState("limit_neutral_session")),
                neutDaily = parse(analyticsDao.getState("limit_neutral_daily")),
                neutOpens = parse(analyticsDao.getState("limit_neutral_opens")),

                breathingDuration = analyticsDao.getState("mindful_base_duration")?.toIntOrNull() ?: 0,
                penaltyMultiplier = analyticsDao.getState("mindful_penalty_multiplier")?.toIntOrNull() ?: 0,
                exemptionWindowSecs = analyticsDao.getState("mindful_exemption_window_secs")?.toIntOrNull() ?: 0,
                doomScrollWindowSecs = analyticsDao.getState("mindful_doomscroll_window_secs")?.toIntOrNull() ?: 0,
                doomScrollThreshold = analyticsDao.getState("mindful_doomscroll_threshold")?.toIntOrNull() ?: 0
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

    fun updateAppLimits(packageName: String, session: Int, daily: Int, opens: Int) {
        viewModelScope.launch {
            appInfoDao.updateAppLimits(packageName, session, daily, opens)
        }
    }

    fun updateGlobalLimits(category: String, session: Int, daily: Int, opens: Int) {
        viewModelScope.launch {
            val prefix = "limit_${category.lowercase()}"
            suspend fun save(key: String, value: Int) {
                analyticsDao.updateState(SystemState("${prefix}_$key", value.toString()))
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
            loadGlobalLimits()
        }
    }
    
    fun updateMasterToggles(master: Boolean, appLimits: Boolean, doomScroll: Boolean) {
        val finalAppLimits = if (!master) false else appLimits
        val finalDoomScroll = if (!master) false else doomScroll
        
        viewModelScope.launch {
            analyticsDao.updateState(SystemState("pulse_master_enabled", master.toString()))
            analyticsDao.updateState(SystemState("pulse_app_limits_enabled", finalAppLimits.toString()))
            analyticsDao.updateState(SystemState("pulse_doomscroll_enabled", finalDoomScroll.toString()))
            loadGlobalLimits()
        }
    }
}
