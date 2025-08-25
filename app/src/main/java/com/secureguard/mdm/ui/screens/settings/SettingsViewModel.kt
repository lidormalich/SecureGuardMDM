package com.secureguard.mdm.ui.screens.settings

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.mdm.R
import com.secureguard.mdm.SecureGuardDeviceAdminReceiver
import com.secureguard.mdm.data.repository.SettingsRepository
import com.secureguard.mdm.features.impl.BlockInternetVpnFeature
import com.secureguard.mdm.features.impl.InstallAndProtectNetGuardFeature
import com.secureguard.mdm.features.registry.CategoryRegistry
import com.secureguard.mdm.security.PasswordManager
import com.secureguard.mdm.settingsfeatures.api.SettingsFeature
import com.secureguard.mdm.settingsfeatures.api.ToggleSetting
import com.secureguard.mdm.settingsfeatures.impl.*
import com.secureguard.mdm.settingsfeatures.registry.SettingsRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


sealed class SettingsSideEffect {
    object NavigateBack : SettingsSideEffect()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val passwordManager: PasswordManager,
    private val dpm: DevicePolicyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _passwordPromptState = MutableStateFlow(PasswordPromptState())
    val passwordPromptState = _passwordPromptState.asStateFlow()

    private val _sideEffect = MutableSharedFlow<SettingsSideEffect>()
    val sideEffect = _sideEffect.asSharedFlow()

    private val _vpnPermissionRequestEvent = MutableSharedFlow<Unit>()
    val vpnPermissionRequestEvent = _vpnPermissionRequestEvent.asSharedFlow()

    private val _triggerUninstallEvent = MutableSharedFlow<Unit>()
    val triggerUninstallEvent = _triggerUninstallEvent.asSharedFlow()

    private val adminComponentName by lazy {
        SecureGuardDeviceAdminReceiver.getComponentName(context)
    }

    // --- NEW: Store initial state for comparison ---
    private var initialProtectionTogglesState: Map<String, Boolean> = emptyMap()
    private var initialSettingsTogglesState: Map<String, Boolean> = emptyMap()

    private var pendingVpnEnableRequest: Boolean = false

    init {
        loadInitialState()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OnToggleProtectionFeature -> handleProtectionToggle(event.featureId, event.isEnabled)
            is SettingsEvent.OnVpnPermissionResult -> handleVpnPermissionResult(event.granted)
            is SettingsEvent.OnToggleSettingChanged -> handleSettingToggle(event.settingId, event.isChecked)
            is SettingsEvent.OnActionSettingClicked -> handleActionClick(event.settingId)
            is SettingsEvent.OnLockSettingsConfirmed -> lockSettings(event.allowManualUpdate)
            is SettingsEvent.OnSaveClick -> saveChanges()
            is SettingsEvent.OnSnackbarShown -> _uiState.update { it.copy(snackbarMessage = null) }
        }
    }

    fun onPasswordPromptEvent(event: PasswordPromptEvent) {
        when (event) {
            is PasswordPromptEvent.OnPasswordEntered -> handleRemoveProtectionPassword(event.password)
            PasswordPromptEvent.OnDismiss -> _passwordPromptState.update { PasswordPromptState() }
        }
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val protectionCategoryToggles = loadProtectionFeatures()
            val settingItemsByCategory = loadSettingsFeatures()

            // --- NEW: Capture the initial state after loading ---
            initialProtectionTogglesState = protectionCategoryToggles.flatMap { it.toggles }.associate { it.feature.id to it.isEnabled }
            initialSettingsTogglesState = settingItemsByCategory.values.flatten().filter { it.feature is ToggleSetting }.associate { it.feature.id to it.isChecked }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    protectionCategoryToggles = protectionCategoryToggles,
                    settingItemsByCategory = settingItemsByCategory
                )
            }
        }
    }

    private suspend fun loadProtectionFeatures(): List<ProtectionCategoryToggle> {
        val isNetGuardInstalled = isNetGuardInstalled()
        val currentDeviceApi = Build.VERSION.SDK_INT
        return CategoryRegistry.allCategories.map { category ->
            val featureToggles = category.features.map { feature ->
                var isSupported = currentDeviceApi >= feature.requiredSdkVersion
                var conflictReason: Int? = null
                if (feature.id == BlockInternetVpnFeature.id && isNetGuardInstalled) {
                    isSupported = false
                    conflictReason = R.string.conflict_reason_netguard_installed
                }
                FeatureToggle(
                    feature = feature,
                    isEnabled = settingsRepository.getFeatureState(feature.id),
                    isSupported = isSupported,
                    requiredApi = feature.requiredSdkVersion,
                    conflictReasonResId = conflictReason
                )
            }
            ProtectionCategoryToggle(titleResId = category.titleResId, toggles = featureToggles)
        }
    }

    private suspend fun loadSettingsFeatures(): Map<com.secureguard.mdm.settingsfeatures.api.SettingCategory, List<SettingItemModel>> {
        // --- START OF MINIMALIST CHANGE ---
        val availableSettings = SettingsRegistry.allSettings.filter { feature ->
            if (feature.id == NavigateToKioskModeSetting.id) {
                // Keep Kiosk setting only if SDK is newer than Nougat (API 24)
                Build.VERSION.SDK_INT > Build.VERSION_CODES.N
            } else {
                true // Keep all other settings
            }
        }
        // --- END OF MINIMALIST CHANGE ---

        return availableSettings // Use the filtered list
            .map { feature ->
                val isChecked = if (feature is ToggleSetting) {
                    when (feature.id) {
                        ToggleUiPositionSetting.id -> settingsRepository.isToggleOnStart()
                        ToggleUiControlTypeSetting.id -> settingsRepository.useCheckbox()
                        ToggleContactEmailSetting.id -> settingsRepository.isContactEmailVisible()
                        ToggleUpdatesSetting.id -> settingsRepository.areAllUpdatesDisabled()
                        ShowBootToastSetting.id -> settingsRepository.isShowBootToastEnabled() // <-- Load initial state
                        else -> false
                    }
                } else false
                SettingItemModel(feature = feature, isChecked = isChecked)
            }
            .groupBy { it.feature.category }
    }


    private fun handleActionClick(settingId: String) {
        when (settingId) {
            LockSettingsAction.id -> {
                // This is handled in the screen, which shows the dialog.
                // The dialog confirmation will call OnLockSettingsConfirmed.
            }
            RemoveProtectionAction.id -> {
                _passwordPromptState.update { it.copy(isVisible = true) }
            }
        }
    }

    private fun handleSettingToggle(settingId: String, isChecked: Boolean) {
        _uiState.update { currentState ->
            val updatedMap = currentState.settingItemsByCategory.toMutableMap()
            for ((category, items) in updatedMap) {
                val updatedItems = items.map { model ->
                    if (model.feature.id == settingId) {
                        model.copy(isChecked = isChecked)
                    } else {
                        model
                    }
                }
                updatedMap[category] = updatedItems
            }
            currentState.copy(settingItemsByCategory = updatedMap)
        }
    }

    private fun saveChanges() {
        viewModelScope.launch {
            val currentState = _uiState.value
            var hasChanges = false
            var snackbarMessage = context.getString(R.string.dialog_changes_saved_successfully)

            // Save new modular settings, ONLY IF CHANGED
            currentState.settingItemsByCategory.values.flatten().forEach { model ->
                if (model.feature is ToggleSetting) {
                    val initialValue = initialSettingsTogglesState[model.feature.id]
                    if (initialValue != model.isChecked) {
                        hasChanges = true
                        when (model.feature.id) {
                            ToggleUiPositionSetting.id -> settingsRepository.setToggleOnStart(model.isChecked)
                            ToggleUiControlTypeSetting.id -> settingsRepository.setUseCheckbox(model.isChecked)
                            ToggleContactEmailSetting.id -> settingsRepository.setContactEmailVisible(model.isChecked)
                            ToggleUpdatesSetting.id -> settingsRepository.setAllUpdatesDisabled(model.isChecked)
                            ShowBootToastSetting.id -> settingsRepository.setShowBootToastEnabled(model.isChecked) // <-- SAVE the new state
                        }
                    }
                }
            }

            // Save main protection features, ONLY IF CHANGED
            val wasNetGuardProtectedBeforeSave = initialProtectionTogglesState[InstallAndProtectNetGuardFeature.id] ?: false
            currentState.protectionCategoryToggles.flatMap { it.toggles }.forEach { toggle ->
                val initialValue = initialProtectionTogglesState[toggle.feature.id]
                if (initialValue != toggle.isEnabled) {
                    hasChanges = true
                    toggle.feature.applyPolicy(context, dpm, adminComponentName, toggle.isEnabled)
                    settingsRepository.setFeatureState(toggle.feature.id, toggle.isEnabled)

                    // Special message for NetGuard
                    if (toggle.feature.id == InstallAndProtectNetGuardFeature.id && wasNetGuardProtectedBeforeSave && !toggle.isEnabled && isNetGuardInstalled()) {
                        snackbarMessage += "\n" + context.getString(R.string.toast_netguard_can_be_uninstalled)
                    }
                }
            }

            if (hasChanges) {
                _uiState.update { it.copy(snackbarMessage = snackbarMessage) }
            }

            // Always navigate back, even if no changes were made.
            _sideEffect.emit(SettingsSideEffect.NavigateBack)
        }
    }


    private fun lockSettings(allowManualUpdate: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAllUpdatesDisabled(true)
            settingsRepository.setAutoUpdateCheckEnabled(false)

            settingsRepository.lockSettingsPermanently(allowManualUpdate)
            Log.d("SettingsVM", "SETTINGS PERMANENTLY LOCKED. Allow manual updates: $allowManualUpdate")
            _sideEffect.emit(SettingsSideEffect.NavigateBack)
        }
    }

    private fun handleProtectionToggle(featureId: String, isEnabled: Boolean) {
        if (featureId == BlockInternetVpnFeature.id && isEnabled) {
            if (VpnService.prepare(context) != null) {
                pendingVpnEnableRequest = true
                viewModelScope.launch { _vpnPermissionRequestEvent.emit(Unit) }
                return
            }
        }
        _uiState.update { currentState ->
            val updatedCategories = currentState.protectionCategoryToggles.map { category ->
                val updatedToggles = category.toggles.map { toggle ->
                    if (toggle.feature.id == featureId) toggle.copy(isEnabled = isEnabled) else toggle
                }
                category.copy(toggles = updatedToggles)
            }
            currentState.copy(protectionCategoryToggles = updatedCategories)
        }
    }

    private fun handleVpnPermissionResult(granted: Boolean) {
        if (granted && pendingVpnEnableRequest) {
            handleProtectionToggle(BlockInternetVpnFeature.id, true)
        } else if (!granted) {
            _uiState.update { it.copy(snackbarMessage = "נדרש אישור להפעלת ה-VPN") }
        }
        pendingVpnEnableRequest = false
    }

    private fun handleRemoveProtectionPassword(password: String) {
        viewModelScope.launch {
            if (passwordManager.verifyPassword(password)) {
                _passwordPromptState.update { it.copy(isVisible = false) }
                initiateRemoval()
            } else {
                _passwordPromptState.update { it.copy(error = "סיסמה שגויה") }
            }
        }
    }

    private fun isNetGuardInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("eu.faircode.netguard", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun initiateRemoval() {
        viewModelScope.launch {
            try {
                _uiState.value.protectionCategoryToggles.flatMap { it.toggles }.forEach {
                    it.feature.applyPolicy(context, dpm, adminComponentName, false)
                    settingsRepository.setFeatureState(it.feature.id, false)
                }

                val blockedApps = settingsRepository.getBlockedAppPackages()
                blockedApps.forEach { packageName ->
                    dpm.setApplicationHidden(adminComponentName, packageName, false)
                }
                settingsRepository.removeAppsFromCache(blockedApps.toList())
                settingsRepository.setBlockedAppPackages(emptySet())
                dpm.clearDeviceOwnerApp(context.packageName)
                _triggerUninstallEvent.emit(Unit)
            } catch (e: SecurityException) {
                _uiState.update { it.copy(snackbarMessage = "שגיאה בהסרת הרשאות הניהול.") }
            }
        }
    }
}

data class PasswordPromptState(
    val isVisible: Boolean = false,
    val error: String? = null
)

sealed class PasswordPromptEvent {
    data class OnPasswordEntered(val password: String) : PasswordPromptEvent()
    object OnDismiss : PasswordPromptEvent()
}