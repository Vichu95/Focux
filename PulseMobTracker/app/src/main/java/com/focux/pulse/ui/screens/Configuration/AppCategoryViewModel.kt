package com.focux.pulse.ui.screens.Configuration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.data.local.entities.AppCategory
import com.focux.pulse.data.local.entities.AppInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * UI state for the App Category section.
 */
data class AppCategoryUiState(
    val productiveApps: List<AppInfo> = emptyList(),
    val distractingApps: List<AppInfo> = emptyList(),
    val neutralApps: List<AppInfo> = emptyList(),
    val ignoredApps: List<AppInfo> = emptyList(),
    val allApps: List<AppInfo> = emptyList(),
    val isEditMode: Boolean = false,
    val searchQuery: String = "",
    val selectedFilters: Set<String> = setOf("Distracting", "Productive", "Neutral", "Ignored")
)

/**
 * ViewModel for the App Category section in Configuration.
 * Observes all apps from the DB and groups them by category.
 * Changes are staged locally and only committed to the DB on Save.
 */
class AppCategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PulseDatabase.getDatabase(application)
    private val appInfoDao = database.appInfoDao()
    private val analyticsDao = database.analyticsDao()
    
    // Instantiate processor to recalculate stats on category change
    private val dailySummaryProcessor = com.focux.pulse.data.processors.DailySummaryProcessor(
        application.applicationContext,
        analyticsDao,
        appInfoDao
    )

    private val _isEditMode = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilters = MutableStateFlow(setOf("Distracting", "Productive", "Neutral", "Ignored"))
    
    // Pending category changes — only committed on Save
    private val _pendingChanges = MutableStateFlow<Map<String, String>>(emptyMap())

    val uiState: StateFlow<AppCategoryUiState> = combine(
        appInfoDao.getAllAppsFlow(),
        _isEditMode,
        _searchQuery,
        _selectedFilters,
        _pendingChanges
    ) { args ->
        val apps = args[0] as List<*>
        @Suppress("UNCHECKED_CAST")
        val appList = apps as List<AppInfo>
        val editMode = args[1] as Boolean
        val query = args[2] as String
        @Suppress("UNCHECKED_CAST")
        val filters = args[3] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val pending = args[4] as Map<String, String>
        // Apply pending changes on top of DB state for live preview
        val patched = appList.map { app ->
            val pendingCategory = pending[app.packageName]
            if (pendingCategory != null) app.copy(category = pendingCategory) else app
        }
        val grouped = patched.groupBy { it.category }
        val filtered = patched.filter { app ->
            filters.any { app.category.equals(it, ignoreCase = true) }
        }
        AppCategoryUiState(
            productiveApps = grouped[AppCategory.PRODUCTIVE].orEmpty(),
            distractingApps = grouped[AppCategory.DISTRACTING].orEmpty(),
            neutralApps = grouped[AppCategory.NEUTRAL].orEmpty(),
            ignoredApps = grouped[AppCategory.IGNORED].orEmpty(),
            allApps = if (query.isBlank()) filtered else filtered.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            },
            isEditMode = editMode,
            searchQuery = query,
            selectedFilters = filters
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppCategoryUiState())

    /** Toggles edit mode on — does NOT commit or discard changes */
    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
        if (!_isEditMode.value) _searchQuery.value = ""
    }

    /** Toggle a category filter chip in edit mode */
    fun toggleFilter(filter: String) {
        val current = _selectedFilters.value
        _selectedFilters.value = if (filter in current) current - filter else current + filter
    }

    /** Stage a category change locally (not yet persisted) */
    fun updateCategory(packageName: String, newCategory: String) {
        _pendingChanges.value = _pendingChanges.value + (packageName to newCategory)
    }

    /** Commit all pending changes to the database */
    fun saveChanges() {
        val changes = _pendingChanges.value
        // Clear UI state immediately — don't wait for DB writes to finish
        _pendingChanges.value = emptyMap()
        _isEditMode.value = false
        _searchQuery.value = ""
        viewModelScope.launch {
            changes.forEach { (pkg, cat) ->
                appInfoDao.updateCategory(pkg, cat)
            }
            // Recalculate daily stats so Top Apps reflect the change
            dailySummaryProcessor.recalculateAllDailyStats()
        }
    }

    /** Discard all pending changes and exit edit mode */
    fun cancelChanges() {
        _pendingChanges.value = emptyMap()
        _isEditMode.value = false
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
