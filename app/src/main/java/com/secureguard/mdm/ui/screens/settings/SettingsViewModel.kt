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
import com.secureguard.mdm.features.registry.FeatureRegistry
import com.secureguard.mdm.security.PasswordManager
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

    private var pendingVpnEnableRequest: Boolean = false

    init {
        loadInitialState()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OnToggleFeature -> handleToggle(event.featureId, event.isEnabled)
            is SettingsEvent.OnSaveClick -> saveChanges()
            is SettingsEvent.OnSnackbarShown -> _uiState.update { it.copy(snackbarMessage = null) }
            is SettingsEvent.OnRemoveProtectionRequest -> _passwordPromptState.update { it.copy(isVisible = true) }
            is SettingsEvent.OnVpnPermissionResult -> handleVpnPermissionResult(event.granted)
            is SettingsEvent.OnAutoUpdateToggled -> _uiState.update { it.copy(isAutoUpdateEnabled = event.isEnabled) }
            is SettingsEvent.OnTogglePositionChanged -> _uiState.update { it.copy(isToggleOnStart = event.isStart) }
            is SettingsEvent.OnControlTypeChanged -> _uiState.update { it.copy(useCheckbox = event.useCheckbox) }
            is SettingsEvent.OnContactEmailVisibilityChanged -> _uiState.update { it.copy(isContactEmailVisible = event.isVisible) }
            is SettingsEvent.OnDisableAllUpdatesChanged -> _uiState.update { it.copy(areAllUpdatesDisabled = event.isDisabled) }
            is SettingsEvent.OnLockSettingsConfirmed -> lockSettings(event.allowManualUpdate)
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
            val isAutoUpdateEnabled = settingsRepository.isAutoUpdateCheckEnabled()
            val isToggleOnStart = settingsRepository.isToggleOnStart()
            val useCheckbox = settingsRepository.useCheckbox()
            val isContactEmailVisible = settingsRepository.isContactEmailVisible()
            val areAllUpdatesDisabled = settingsRepository.areAllUpdatesDisabled()
            val isNetGuardInstalled = isNetGuardInstalled()
            val currentDeviceApi = Build.VERSION.SDK_INT

            val categoryToggles = CategoryRegistry.allCategories.map { category ->
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
                CategoryToggle(titleResId = category.titleResId, toggles = featureToggles)
            }
            _uiState.update { it.copy(
                categoryToggles = categoryToggles,
                isLoading = false,
                isAutoUpdateEnabled = isAutoUpdateEnabled,
                isToggleOnStart = isToggleOnStart,
                useCheckbox = useCheckbox,
                isContactEmailVisible = isContactEmailVisible,
                areAllUpdatesDisabled = areAllUpdatesDisabled
            ) }
        }
    }

    private fun saveChanges() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val wasNetGuardProtectedBeforeSave = settingsRepository.getFeatureState(InstallAndProtectNetGuardFeature.id)
            val isNetGuardProtectionBeingDisabled = wasNetGuardProtectedBeforeSave &&
                    currentState.categoryToggles.flatMap { it.toggles }
                        .find { it.feature.id == InstallAndProtectNetGuardFeature.id }?.isEnabled == false

            currentState.categoryToggles.flatMap { it.toggles }.forEach { toggle ->
                toggle.feature.applyPolicy(context, dpm, adminComponentName, toggle.isEnabled)
                settingsRepository.setFeatureState(toggle.feature.id, toggle.isEnabled)
            }
            settingsRepository.setAutoUpdateCheckEnabled(currentState.isAutoUpdateEnabled)
            settingsRepository.setToggleOnStart(currentState.isToggleOnStart)
            settingsRepository.setUseCheckbox(currentState.useCheckbox)
            settingsRepository.setContactEmailVisible(currentState.isContactEmailVisible)
            settingsRepository.setAllUpdatesDisabled(currentState.areAllUpdatesDisabled)

            var snackbarMessage = "ההגדרות נשמרו בהצלחה!"
            if (isNetGuardProtectionBeingDisabled && isNetGuardInstalled()) {
                snackbarMessage += "\n" + context.getString(R.string.toast_netguard_can_be_uninstalled)
            }

            _uiState.update { it.copy(snackbarMessage = snackbarMessage) }
            loadInitialState()
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

    private fun handleToggle(featureId: String, isEnabled: Boolean) {
        if (featureId == BlockInternetVpnFeature.id && isEnabled) {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                pendingVpnEnableRequest = true
                viewModelScope.launch { _vpnPermissionRequestEvent.emit(Unit) }
                return
            }
        }
        _uiState.update { currentState ->
            val updatedCategories = currentState.categoryToggles.map { category ->
                val updatedToggles = category.toggles.map { toggle ->
                    if (toggle.feature.id == featureId) toggle.copy(isEnabled = isEnabled) else toggle
                }
                category.copy(toggles = updatedToggles)
            }
            currentState.copy(categoryToggles = updatedCategories)
        }
    }

    private fun handleVpnPermissionResult(granted: Boolean) {
        if (granted && pendingVpnEnableRequest) {
            handleToggle(BlockInternetVpnFeature.id, true)
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

    @Suppress("DEPRECATION")
    private fun initiateRemoval() {
        viewModelScope.launch {
            try {
                Log.d("SettingsVM", "Disabling all protection features...")
                FeatureRegistry.allFeatures.forEach { feature ->
                    feature.applyPolicy(context, dpm, adminComponentName, false)
                    settingsRepository.setFeatureState(feature.id, false)
                }
                Log.d("SettingsVM", "All features policies have been disabled.")

                Log.d("SettingsVM", "Unhiding all blocked applications...")
                val blockedApps = settingsRepository.getBlockedAppPackages()
                if (blockedApps.isNotEmpty()) {
                    blockedApps.forEach { packageName ->
                        try {
                            dpm.setApplicationHidden(adminComponentName, packageName, false)
                        } catch (e: Exception) {
                            Log.e("SettingsVM", "Failed to unhide app: $packageName", e)
                        }
                    }
                    Log.d("SettingsVM", "All apps have been unhidden.")

                    Log.d("SettingsVM", "Cleaning up app blocker storage...")
                    settingsRepository.removeAppsFromCache(blockedApps.toList())
                    settingsRepository.setBlockedAppPackages(emptySet())
                    Log.d("SettingsVM", "App blocker storage cleared.")
                } else {
                    Log.d("SettingsVM", "No blocked apps to unhide or clean up.")
                }

                Log.d("SettingsVM", "Clearing device owner...")
                dpm.clearDeviceOwnerApp(context.packageName)
                _triggerUninstallEvent.emit(Unit)
                Log.d("SettingsVM", "Device owner cleared and uninstall triggered.")

            } catch (e: SecurityException) {
                Log.e("SettingsVM", "Failed to clear device owner.", e)
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