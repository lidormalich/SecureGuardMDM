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
    val isAutoUpdateEnabled: Boolean = true,

    // --- מצבים חדשים להתאמה אישית ונעילה ---
    val isToggleOnStart: Boolean = false,
    val useCheckbox: Boolean = false,
    val isContactEmailVisible: Boolean = true,
    val areAllUpdatesDisabled: Boolean = false
)

sealed class SettingsEvent {
    data class OnToggleFeature(val featureId: String, val isEnabled: Boolean) : SettingsEvent()
    object OnSaveClick : SettingsEvent()
    object OnSnackbarShown : SettingsEvent()
    object OnRemoveProtectionRequest : SettingsEvent()
    data class OnVpnPermissionResult(val granted: Boolean) : SettingsEvent()
    data class OnAutoUpdateToggled(val isEnabled: Boolean) : SettingsEvent()

    // --- אירועים חדשים ---
    data class OnTogglePositionChanged(val isStart: Boolean) : SettingsEvent()
    data class OnControlTypeChanged(val useCheckbox: Boolean) : SettingsEvent()
    data class OnContactEmailVisibilityChanged(val isVisible: Boolean) : SettingsEvent()
    data class OnDisableAllUpdatesChanged(val isDisabled: Boolean) : SettingsEvent()
    data class OnLockSettingsConfirmed(val allowManualUpdate: Boolean) : SettingsEvent() // <-- שינוי כאן
}