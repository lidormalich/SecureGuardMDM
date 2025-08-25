package com.secureguard.mdm.kiosk.vm

import androidx.compose.ui.graphics.Color
import com.secureguard.mdm.appblocker.AppInfo

data class KioskManagementUiState(
    val isLoading: Boolean = true,
    val isKioskModeEnabled: Boolean = false,
    val snackbarMessage: String? = null,

    val currentLauncherName: String? = null,
    val currentLauncherPackage: String? = null,
    val isLauncherBlocked: Boolean = false,

    // --- NEW ---
    val isSettingsInLockTaskEnabled: Boolean = true,

    // State for Kiosk App Selection Screen
    val isAppListLoading: Boolean = true,
    val allLauncherApps: List<AppInfo> = emptyList(),
    val displayedApps: List<AppInfo> = emptyList(),
    val selectedKioskApps: Set<String> = emptySet(),
    val searchQuery: String = "",

    // State for Kiosk Customization Screen
    val isCustomizationLoading: Boolean = true,
    val kioskTitle: String = "",
    val kioskBackgroundColor: Color = Color.DarkGray,
    val kioskPrimaryColor: Color = Color(0xFF6200EE.toInt()),
    val showSecureUpdate: Boolean = true,
    val selectedActionButtons: Set<String> = emptySet()
)

sealed class KioskManagementEvent {
    // Main Screen Events
    data class OnKioskModeToggle(val isEnabled: Boolean) : KioskManagementEvent()
    object OnSnackbarShown : KioskManagementEvent()
    data class OnBlockLauncherDialogResponse(val shouldBlock: Boolean) : KioskManagementEvent()

    data class OnBlockLauncherToggle(val shouldBlock: Boolean) : KioskManagementEvent()
    object OnBlockLauncherConfirmed : KioskManagementEvent()

    // --- NEW ---
    data class OnSettingsInLockTaskToggle(val isEnabled: Boolean) : KioskManagementEvent()

    // App Selection Screen Events
    object LoadKioskApps : KioskManagementEvent()
    data class OnSearchQueryChanged(val query: String) : KioskManagementEvent()
    data class OnAppSelectionChanged(val packageName: String, val isSelected: Boolean) : KioskManagementEvent()
    object OnSaveKioskApps : KioskManagementEvent()
    object OnResetKioskLayout : KioskManagementEvent()

    // Customization Screen Events
    object LoadKioskCustomization : KioskManagementEvent()
    data class OnTitleChanged(val title: String) : KioskManagementEvent()
    data class OnColorSelected(val color: Color) : KioskManagementEvent()
    data class OnPrimaryColorSelected(val color: Color) : KioskManagementEvent()
    data class OnShowSecureUpdateToggle(val show: Boolean) : KioskManagementEvent()
    data class OnActionButtonToggle(val buttonId: String, val isSelected: Boolean) : KioskManagementEvent()
    object OnSaveKioskCustomization : KioskManagementEvent()
}

sealed class KioskManagementSideEffect {
    data class ShowToast(val message: String) : KioskManagementSideEffect()
    data class ShowBlockLauncherDialog(val launcherName: String) : KioskManagementSideEffect()
    object ShowBlockLauncherWarningDialog : KioskManagementSideEffect()
    // --- NEW ---
    object ShowSettingsInLockTaskWarningDialog : KioskManagementSideEffect()
}