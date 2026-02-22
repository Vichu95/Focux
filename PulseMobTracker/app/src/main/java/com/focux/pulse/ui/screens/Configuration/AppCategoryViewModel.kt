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
    val searchQuery: String = ""
)

/**
 * ViewModel for the App Category section in Configuration.
 * Observes all apps from the DB and groups them by category.
 */
class AppCategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val appInfoDao = PulseDatabase.getDatabase(application).appInfoDao()

    private val _isEditMode = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<AppCategoryUiState> = combine(
        appInfoDao.getAllAppsFlow(),
        _isEditMode,
        _searchQuery
    ) { apps, editMode, query ->
        val grouped = apps.groupBy { it.category }
        AppCategoryUiState(
            productiveApps = grouped[AppCategory.PRODUCTIVE].orEmpty(),
            distractingApps = grouped[AppCategory.DISTRACTING].orEmpty(),
            neutralApps = grouped[AppCategory.NEUTRAL].orEmpty(),
            ignoredApps = grouped[AppCategory.IGNORED].orEmpty(),
            allApps = if (query.isBlank()) apps else apps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            },
            isEditMode = editMode,
            searchQuery = query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppCategoryUiState())

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
        if (!_isEditMode.value) _searchQuery.value = "" // Clear search on exit
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCategory(packageName: String, newCategory: String) {
        viewModelScope.launch {
            appInfoDao.updateCategory(packageName, newCategory)
        }
    }
}
