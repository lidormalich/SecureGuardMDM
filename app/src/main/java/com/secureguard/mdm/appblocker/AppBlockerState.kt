package com.secureguard.mdm.appblocker

enum class AppFilterType {
    USER_ONLY,
    ALL_EXCEPT_CORE,
    LAUNCHER_ONLY,
    ALL
}

data class AppBlockerUiState(
    val displayedAppsForSelection: List<AppInfo> = emptyList(),
    val displayedBlockedApps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val currentFilter: AppFilterType = AppFilterType.USER_ONLY,
    val searchQuery: String = "",
    val selectionForUnblock: Set<String> = emptySet()
)

sealed class AppBlockerEvent {
    data class OnFilterChanged(val newFilter: AppFilterType) : AppBlockerEvent()
    data class OnAppSelectionChanged(val packageName: String, val isBlocked: Boolean) : AppBlockerEvent()
    object OnSaveRequest : AppBlockerEvent() // יישמר מיידית
    object OnDismissPasswordPrompt : AppBlockerEvent() // נשאר למקרה שימוש עתידי, לא מזיק
    data class OnAddPackageManually(val packageName: String) : AppBlockerEvent()
    data class OnToggleUnblockSelection(val packageName: String) : AppBlockerEvent()
    object OnUnblockSelectedRequest : AppBlockerEvent() // ישוחרר מיידית
    data class OnSearchQueryChanged(val query: String) : AppBlockerEvent()
}