package com.secureguard.mdm.kiosk.vm

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.mdm.R
import com.secureguard.mdm.SecureGuardDeviceAdminReceiver
import com.secureguard.mdm.appblocker.AppInfo
import com.secureguard.mdm.data.repository.SettingsRepository
import com.secureguard.mdm.kiosk.manager.KioskManager
import com.secureguard.mdm.kiosk.model.KioskActionButton
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class KioskManagementViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val kioskManager: KioskManager,
    private val dpm: DevicePolicyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(KioskManagementUiState())
    val uiState = _uiState.asStateFlow()

    private val _sideEffect = MutableSharedFlow<KioskManagementSideEffect>()
    val sideEffect = _sideEffect.asSharedFlow()

    private val adminComponentName by lazy {
        SecureGuardDeviceAdminReceiver.getComponentName(context)
    }

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val isEnabled = settingsRepository.isKioskModeEnabled()
            val launcherInfo = kioskManager.getBlockableLauncherInfo()
            val isLauncherBlocked = launcherInfo?.let { dpm.isApplicationHidden(adminComponentName, it.packageName) } ?: false
            val isSettingsInLockTaskEnabled = settingsRepository.isKioskSettingsInLockTaskEnabled()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isKioskModeEnabled = isEnabled,
                    currentLauncherName = launcherInfo?.appName,
                    currentLauncherPackage = launcherInfo?.packageName,
                    isLauncherBlocked = isLauncherBlocked,
                    isSettingsInLockTaskEnabled = isSettingsInLockTaskEnabled
                )
            }
        }
    }

    fun onEvent(event: KioskManagementEvent) {
        when (event) {
            is KioskManagementEvent.OnKioskModeToggle -> handleKioskToggle(event.isEnabled)
            is KioskManagementEvent.OnBlockLauncherToggle -> handleBlockLauncherToggle(event.shouldBlock)
            is KioskManagementEvent.OnBlockLauncherConfirmed -> blockLauncher()
            is KioskManagementEvent.OnSettingsInLockTaskToggle -> handleSettingsInLockTaskToggle(event.isEnabled)
            is KioskManagementEvent.OnBlockLauncherDialogResponse -> onBlockLauncherResponse(event.shouldBlock)
            is KioskManagementEvent.OnSnackbarShown -> _uiState.update { it.copy(snackbarMessage = null) }
            is KioskManagementEvent.LoadKioskApps -> loadKioskApps()
            is KioskManagementEvent.OnSearchQueryChanged -> onSearchQueryChanged(event.query)
            is KioskManagementEvent.OnAppSelectionChanged -> onAppSelectionChanged(event.packageName, event.isSelected)
            is KioskManagementEvent.OnSaveKioskApps -> saveKioskApps()
            is KioskManagementEvent.OnResetKioskLayout -> resetKioskLayout()
            is KioskManagementEvent.LoadKioskCustomization -> loadKioskCustomization()
            is KioskManagementEvent.OnTitleChanged -> _uiState.update { it.copy(kioskTitle = event.title) }
            is KioskManagementEvent.OnColorSelected -> _uiState.update { it.copy(kioskBackgroundColor = event.color) }
            is KioskManagementEvent.OnPrimaryColorSelected -> _uiState.update { it.copy(kioskPrimaryColor = event.color) }
            is KioskManagementEvent.OnShowSecureUpdateToggle -> _uiState.update { it.copy(showSecureUpdate = event.show) }
            is KioskManagementEvent.OnActionButtonToggle -> onActionButtonToggle(event.buttonId, event.isSelected)
            is KioskManagementEvent.OnSaveKioskCustomization -> saveKioskCustomization()
        }
    }

    private fun handleSettingsInLockTaskToggle(isEnabled: Boolean) {
        if (!isEnabled) {
            viewModelScope.launch {
                _sideEffect.emit(KioskManagementSideEffect.ShowSettingsInLockTaskWarningDialog)
            }
        } else {
            updateSettingsInLockTask(true)
        }
    }

    fun updateSettingsInLockTask(isEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKioskSettingsInLockTaskEnabled(isEnabled)
            _uiState.update { it.copy(isSettingsInLockTaskEnabled = isEnabled) }
            updateLockTaskPackages()
            _sideEffect.emit(KioskManagementSideEffect.ShowToast(context.getString(R.string.dialog_changes_saved_successfully)))
        }
    }

    private fun enableKioskMode(shouldBlockLauncher: Boolean) {
        viewModelScope.launch {
            if (shouldBlockLauncher) {
                kioskManager.getBlockableLauncherInfo()?.let {
                    kioskManager.blockLauncher(it.packageName)
                    settingsRepository.setKioskBlockedLauncherPackage(it.packageName)
                }
            }
            kioskManager.setKioskAsHomeLauncher(true)
            settingsRepository.setKioskModeEnabled(true)
            _uiState.update { it.copy(isKioskModeEnabled = true) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // For Android 9+: Lock status bar, notifications, etc.
                dpm.setLockTaskFeatures(adminComponentName, DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO)
            }
            updateLockTaskPackages()

            _sideEffect.emit(KioskManagementSideEffect.ShowToast(context.getString(R.string.kiosk_toast_enabled)))
        }
    }

    private fun disableKioskMode() {
        viewModelScope.launch {
            if (_uiState.value.isLauncherBlocked) {
                unblockLauncher()
            }
            settingsRepository.getKioskBlockedLauncherPackage()?.let {
                kioskManager.unblockLauncher(it)
                settingsRepository.setKioskBlockedLauncherPackage(null)
            }
            kioskManager.setKioskAsHomeLauncher(false)
            settingsRepository.setKioskModeEnabled(false)

            dpm.setLockTaskPackages(adminComponentName, emptyArray())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(adminComponentName, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }

            _uiState.update { it.copy(isKioskModeEnabled = false, isLauncherBlocked = false) }
            _sideEffect.emit(KioskManagementSideEffect.ShowToast(context.getString(R.string.kiosk_toast_disabled)))
        }
    }

    private fun saveKioskApps() {
        viewModelScope.launch {
            settingsRepository.setKioskAppPackages(_uiState.value.selectedKioskApps)
            settingsRepository.setKioskLayoutJson(null)
            updateLockTaskPackages()
            _sideEffect.emit(KioskManagementSideEffect.ShowToast(context.getString(R.string.dialog_changes_saved_successfully)))
        }
    }

    private suspend fun updateLockTaskPackages() {
        val selectedApps = settingsRepository.getKioskAppPackages()
        val packagesForLockTask = selectedApps.toMutableSet()
        packagesForLockTask.add(context.packageName) // Kiosk app itself must be whitelisted

        // For Android 9+, check the toggle. For older versions, always allow settings for safety.
        val allowSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            settingsRepository.isKioskSettingsInLockTaskEnabled()
        } else {
            true // Safety fallback for older devices
        }

        if (allowSettings) {
            packagesForLockTask.add("com.android.settings")
        }

        dpm.setLockTaskPackages(adminComponentName, packagesForLockTask.toTypedArray())
    }

    private fun handleBlockLauncherToggle(shouldBlock: Boolean) {
        if (shouldBlock) {
            viewModelScope.launch {
                _sideEffect.emit(KioskManagementSideEffect.ShowBlockLauncherWarningDialog)
            }
        } else {
            unblockLauncher()
        }
    }

    private fun blockLauncher() {
        viewModelScope.launch {
            _uiState.value.currentLauncherPackage?.let {
                kioskManager.blockLauncher(it)
                _uiState.update { state -> state.copy(isLauncherBlocked = true) }
                _sideEffect.emit(KioskManagementSideEffect.ShowToast("${_uiState.value.currentLauncherName} הוסתר."))
            }
        }
    }

    private fun unblockLauncher() {
        viewModelScope.launch {
            _uiState.value.currentLauncherPackage?.let {
                if (dpm.isApplicationHidden(adminComponentName, it)) {
                    kioskManager.unblockLauncher(it)
                    _sideEffect.emit(KioskManagementSideEffect.ShowToast("${_uiState.value.currentLauncherName} שוחזר."))
                }
                _uiState.update { state -> state.copy(isLauncherBlocked = false) }
            }
        }
    }

    private fun handleKioskToggle(enable: Boolean) {
        if (enable) {
            val launcherInfo = kioskManager.getBlockableLauncherInfo()
            if (launcherInfo != null) {
                viewModelScope.launch {
                    _sideEffect.emit(KioskManagementSideEffect.ShowBlockLauncherDialog(launcherInfo.appName))
                }
            } else {
                enableKioskMode(shouldBlockLauncher = false)
            }
        } else {
            disableKioskMode()
        }
    }

    private fun onBlockLauncherResponse(shouldBlock: Boolean) {
        enableKioskMode(shouldBlock)
    }

    private fun loadKioskApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAppListLoading = true) }
            val selectedPackages = settingsRepository.getKioskAppPackages()
            val allApps = getLauncherApps(selectedPackages)
            _uiState.update {
                it.copy(
                    isAppListLoading = false,
                    allLauncherApps = allApps,
                    displayedApps = allApps,
                    selectedKioskApps = selectedPackages,
                    searchQuery = ""
                )
            }
        }
    }

    private suspend fun getLauncherApps(selectedPackages: Set<String>): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return@withContext pm.queryIntentActivities(mainIntent, 0).mapNotNull {
            it.activityInfo?.let { info ->
                AppInfo(
                    appName = info.loadLabel(pm).toString(),
                    packageName = info.packageName,
                    icon = info.loadIcon(pm),
                    isBlocked = selectedPackages.contains(info.packageName),
                    isSystemApp = false,
                    isLauncherApp = true,
                    isInstalled = true
                )
            }
        }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
    }

    private fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyAppFilter()
    }

    private fun onAppSelectionChanged(packageName: String, isSelected: Boolean) {
        _uiState.update { currentState ->
            val newSelection = currentState.selectedKioskApps.toMutableSet()
            if (isSelected) {
                newSelection.add(packageName)
            } else {
                newSelection.remove(packageName)
            }
            currentState.copy(selectedKioskApps = newSelection)
        }
        applyAppFilter()
    }

    private fun applyAppFilter() {
        _uiState.update { currentState ->
            val query = currentState.searchQuery.lowercase()
            val filteredList = if (query.isBlank()) {
                currentState.allLauncherApps
            } else {
                currentState.allLauncherApps.filter {
                    it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
                }
            }
            val updatedDisplayedList = filteredList.map {
                it.copy(isBlocked = currentState.selectedKioskApps.contains(it.packageName))
            }
            currentState.copy(displayedApps = updatedDisplayedList)
        }
    }

    private fun resetKioskLayout() {
        viewModelScope.launch {
            settingsRepository.setKioskLayoutJson(null)
            _sideEffect.emit(KioskManagementSideEffect.ShowToast("פריסת הקיוסק אופסה. כדי לראות את השינוי, יש להפעיל מחדש את מצב הקיוסק."))
        }
    }

    private fun loadKioskCustomization() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCustomizationLoading = true) }
            val title = settingsRepository.getKioskTitle()
            val bgColor = Color(settingsRepository.getKioskBackgroundColor())
            val primaryColor = Color(settingsRepository.getKioskPrimaryColor())
            val showUpdate = settingsRepository.shouldShowKioskSecureUpdate()
            val buttons = settingsRepository.getKioskActionButtons()
            _uiState.update {
                it.copy(
                    isCustomizationLoading = false,
                    kioskTitle = title,
                    kioskBackgroundColor = bgColor,
                    kioskPrimaryColor = primaryColor,
                    showSecureUpdate = showUpdate,
                    selectedActionButtons = buttons
                )
            }
        }
    }

    private fun onActionButtonToggle(buttonId: String, isSelected: Boolean) {
        if ((buttonId == KioskActionButton.WIFI.id || buttonId == KioskActionButton.BLUETOOTH.id) && isSelected) {
            viewModelScope.launch {
                _sideEffect.emit(KioskManagementSideEffect.ShowToast(context.getString(R.string.kiosk_toast_feature_unavailable)))
            }
            return
        }

        _uiState.update { currentState ->
            val newSelection = currentState.selectedActionButtons.toMutableSet()
            if (isSelected) newSelection.add(buttonId) else newSelection.remove(buttonId)
            currentState.copy(selectedActionButtons = newSelection)
        }
    }

    private fun saveKioskCustomization() {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.setKioskTitle(state.kioskTitle)
            settingsRepository.setKioskBackgroundColor(state.kioskBackgroundColor.toArgb())
            settingsRepository.setKioskPrimaryColor(state.kioskPrimaryColor.toArgb())
            settingsRepository.setShouldShowKioskSecureUpdate(state.showSecureUpdate)
            settingsRepository.setKioskActionButtons(state.selectedActionButtons)
            _sideEffect.emit(KioskManagementSideEffect.ShowToast(context.getString(R.string.dialog_changes_saved_successfully)))
        }
    }
}