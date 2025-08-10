package com.secureguard.mdm.ui.screens.settings

// Data class to hold a category title and its list of toggles
data class CategoryToggle(
    val titleResId: Int,
    val toggles: List<FeatureToggle>
)

// Data class to hold the UI state for a single toggle
data class FeatureToggle(
    val feature: com.secureguard.mdm.features.api.ProtectionFeature,
    val isEnabled: Boolean,
    val isSupported: Boolean = true,
    val requiredApi: Int = 0,
    val conflictReasonResId: Int? = null
)

data class SettingsUiState(
    val categoryToggles: List<CategoryToggle> = emptyList(),
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null,
    val isAutoUpdateEnabled: Boolean = true // <-- מצב חדש
)

sealed class SettingsEvent {
    data class OnToggleFeature(val featureId: String, val isEnabled: Boolean) : SettingsEvent()
    object OnSaveClick : SettingsEvent()
    object OnSnackbarShown : SettingsEvent()
    object OnRemoveProtectionRequest : SettingsEvent()
    data class OnVpnPermissionResult(val granted: Boolean) : SettingsEvent()
    data class OnAutoUpdateToggled(val isEnabled: Boolean) : SettingsEvent() // <-- אירוע חדש
}