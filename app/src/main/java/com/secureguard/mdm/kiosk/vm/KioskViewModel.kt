package com.secureguard.mdm.kiosk.vm

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.secureguard.mdm.R
import com.secureguard.mdm.appblocker.AppInfo
import com.secureguard.mdm.data.repository.SettingsRepository
import com.secureguard.mdm.kiosk.model.KioskActionButton
import com.secureguard.mdm.kiosk.model.KioskApp
import com.secureguard.mdm.kiosk.model.KioskFolder
import com.secureguard.mdm.kiosk.model.KioskItem
import com.secureguard.mdm.security.PasswordManager
import com.secureguard.mdm.utils.SecureUpdateHelper
import com.secureguard.mdm.utils.UpdateVerificationResult
import com.secureguard.mdm.utils.update.UpdateManager
import com.secureguard.mdm.utils.update.UpdateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min

@HiltViewModel
class KioskViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val passwordManager: PasswordManager,
    private val gson: Gson,
    private val updateManager: UpdateManager,
    private val secureUpdateHelper: SecureUpdateHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(KioskUiState())
    val uiState = _uiState.asStateFlow()

    private val _sideEffect = MutableSharedFlow<KioskSideEffect>()
    val sideEffect = _sideEffect.asSharedFlow()

    init {
        onEvent(KioskEvent.LoadData)
    }

    fun onEvent(event: KioskEvent) {
        when (event) {
            is KioskEvent.LoadData -> loadKioskData()
            is KioskEvent.SaveLayout -> saveLayout()
            is KioskEvent.RefreshLayout -> loadKioskData(showLoading = false) // --- NEW ---
            is KioskEvent.OnItemClick -> handleItemClick(event.item)
            is KioskEvent.OnItemLongPress -> _uiState.update { it.copy(contextMenuItemId = event.item.id) } // --- NEW ---
            is KioskEvent.DismissContextMenu -> _uiState.update { it.copy(contextMenuItemId = null) } // --- NEW ---
            is KioskEvent.OnItemsMoved -> handleItemsMoved(event.from, event.to)
            is KioskEvent.OnItemsMerged -> handleItemsMerged(event.from, event.to)
            is KioskEvent.OnCreateFolderConfirmed -> createFolder(event.folderName, event.app1, event.app2)
            is KioskEvent.RequestRenameFolder -> requestRenameFolder(event.folder) // --- NEW ---
            is KioskEvent.OnRenameFolderConfirmed -> renameFolder(event.folderId, event.newName) // --- NEW ---
            is KioskEvent.DisbandFolder -> disbandFolder(event.folder) // --- NEW ---
            is KioskEvent.OnActionButtonClick -> handleActionButton(event.action)
            is KioskEvent.OnSettingsClick -> viewModelScope.launch { _sideEffect.emit(KioskSideEffect.ShowPasswordPrompt) }
            is KioskEvent.OnInfoClick -> viewModelScope.launch { _sideEffect.emit(KioskSideEffect.ShowInfoDialog) }
            is KioskEvent.OnPasswordEntered -> verifyPasswordAndNavigate(event.password)
            is KioskEvent.OnManualUpdateCheck -> checkForUpdates()
            is KioskEvent.OnUpdateFileSelected -> event.uri?.let { handleSecureUpdate(it) }
        }
    }

    private fun disbandFolder(folder: KioskFolder) {
        val currentList = _uiState.value.kioskItems.toMutableList()
        val index = currentList.indexOfFirst { it.id == folder.id }
        if (index != -1) {
            currentList.removeAt(index)
            currentList.addAll(index, folder.apps)
            _uiState.update { it.copy(kioskItems = currentList, contextMenuItemId = null) }
        }
    }

    private fun requestRenameFolder(folder: KioskFolder) {
        viewModelScope.launch {
            _sideEffect.emit(KioskSideEffect.ShowRenameFolderDialog(folder))
            _uiState.update { it.copy(contextMenuItemId = null) }
        }
    }

    private fun renameFolder(folderId: String, newName: String) {
        val updatedList = _uiState.value.kioskItems.map {
            if (it.id == folderId && it is KioskFolder) {
                it.copy(name = newName.ifBlank { it.name })
            } else {
                it
            }
        }
        _uiState.update { it.copy(kioskItems = updatedList) }
    }


    private fun handleSecureUpdate(uri: Uri) {
        viewModelScope.launch {
            when (val result = secureUpdateHelper.verifyUpdate(uri)) {
                is UpdateVerificationResult.Success -> _sideEffect.emit(KioskSideEffect.InstallUpdate(uri))
                is UpdateVerificationResult.Failure -> _sideEffect.emit(KioskSideEffect.ShowToast(result.errorMessage))
            }
        }
    }

    private fun handleItemClick(item: KioskItem) {
        viewModelScope.launch {
            when (item) {
                is KioskApp -> _sideEffect.emit(KioskSideEffect.LaunchApp(item.appInfo.packageName))
                is KioskFolder -> _sideEffect.emit(KioskSideEffect.ShowFolderContents(item))
            }
        }
    }


    private fun loadKioskData(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true) }
            }

            val title = settingsRepository.getKioskTitle()
            val bgColor = Color(settingsRepository.getKioskBackgroundColor())
            val primaryColor = Color(settingsRepository.getKioskPrimaryColor())
            val showUpdate = settingsRepository.shouldShowKioskSecureUpdate()
            val buttonIds = settingsRepository.getKioskActionButtons()
            var actionButtons = KioskActionButton.entries.filter { buttonIds.contains(it.id) }
            val isOfficial = secureUpdateHelper.isOfficialBuild()
            val isContactVisible = settingsRepository.isContactEmailVisible()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                actionButtons = actionButtons.filterNot {
                    it == KioskActionButton.WIFI ||
                            it == KioskActionButton.BLUETOOTH ||
                            it == KioskActionButton.LOCATION
                }
            }

            val kioskItems = loadAndParseKioskLayout()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    kioskTitle = title,
                    backgroundColor = bgColor,
                    primaryColor = primaryColor,
                    showSecureUpdate = showUpdate,
                    kioskItems = kioskItems,
                    actionButtons = actionButtons,
                    isOfficialBuild = isOfficial,
                    isContactEmailVisible = isContactVisible
                )
            }
        }
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            _sideEffect.emit(KioskSideEffect.ShowToast(context.getString(R.string.update_check_checking)))
            when (val result = updateManager.checkForUpdate()) {
                is UpdateResult.UpdateAvailable -> _sideEffect.emit(KioskSideEffect.ShowUpdateAvailable(result.info))
                is UpdateResult.Failure -> _sideEffect.emit(KioskSideEffect.ShowUpdateCheckFailed(result.message))
                is UpdateResult.NoUpdate -> _sideEffect.emit(KioskSideEffect.ShowNoUpdateAvailable)
            }
        }
    }

    private suspend fun loadAndParseKioskLayout(): List<KioskItem> {
        val jsonLayout = settingsRepository.getKioskLayoutJson()
        if (!jsonLayout.isNullOrBlank()) {
            try {
                val type = object : TypeToken<ArrayList<KioskItem>>() {}.type
                val parsedItems: List<KioskItem> = gson.fromJson(jsonLayout, type)

                return withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    parsedItems.mapNotNull { item ->
                        try {
                            when (item) {
                                is KioskApp -> {
                                    val appInfoFromPm = pm.getApplicationInfo(item.appInfo.packageName, 0)
                                    item.copy(appInfo = item.appInfo.copy(icon = appInfoFromPm.loadIcon(pm)))
                                }
                                is KioskFolder -> {
                                    val hydratedApps = item.apps.mapNotNull { appInFolder ->
                                        try {
                                            val appInfoFromPm = pm.getApplicationInfo(appInFolder.appInfo.packageName, 0)
                                            appInFolder.copy(appInfo = appInFolder.appInfo.copy(icon = appInfoFromPm.loadIcon(pm)))
                                        } catch (e: PackageManager.NameNotFoundException) {
                                            null
                                        }
                                    }.toMutableList()
                                    item.copy(apps = hydratedApps)
                                }
                            }
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("KioskViewModel", "Failed to parse Kiosk layout JSON", e)
                settingsRepository.setKioskLayoutJson(null)
            }
        }


        val appPackages = settingsRepository.getKioskAppPackages()
        return resolveKioskApps(appPackages)
    }

    private suspend fun resolveKioskApps(packageNames: Set<String>): List<KioskItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        packageNames.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val resolvedAppInfo = AppInfo(
                    appName = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm),
                    isBlocked = false, isSystemApp = false, isLauncherApp = true, isInstalled = true
                )
                KioskApp(resolvedAppInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedBy { (it as KioskApp).appInfo.appName.lowercase() }
    }

    private fun handleItemsMoved(from: Int, to: Int) {
        val currentList = _uiState.value.kioskItems.toMutableList()
        if (from >= 0 && from < currentList.size && to >= 0 && to < currentList.size) {
            val item = currentList.removeAt(from)
            currentList.add(to, item)
            _uiState.update { it.copy(kioskItems = currentList) }
        }
    }

    private fun handleItemsMerged(from: Int, to: Int) {
        val currentList = _uiState.value.kioskItems
        val fromItem = currentList.getOrNull(from) as? KioskApp ?: return
        val toItem = currentList.getOrNull(to)

        when (toItem) {
            is KioskApp -> { // Merge two apps, prompt to create a folder
                viewModelScope.launch {
                    _sideEffect.emit(KioskSideEffect.ShowCreateFolderDialog(toItem, fromItem))
                }
            }
            is KioskFolder -> { // Add app to existing folder
                val updatedList = currentList.toMutableList().apply {
                    val targetFolder = get(to) as KioskFolder
                    targetFolder.apps.add(fromItem)
                    removeAt(from)
                }
                _uiState.update { it.copy(kioskItems = updatedList) }
            }
            else -> {}
        }
    }

    private fun createFolder(folderName: String, app1: KioskApp, app2: KioskApp) {
        val currentList = _uiState.value.kioskItems.toMutableList()

        val newFolder = KioskFolder(
            id = UUID.randomUUID().toString(),
            name = folderName.ifBlank { "Folder" },
            apps = mutableListOf(app1, app2)
        )

        val index1 = currentList.indexOfFirst { it.id == app1.id }
        val index2 = currentList.indexOfFirst { it.id == app2.id }

        if (index1 == -1 || index2 == -1) return

        if (index1 > index2) {
            currentList.removeAt(index1)
            currentList.removeAt(index2)
        } else {
            currentList.removeAt(index2)
            currentList.removeAt(index1)
        }

        val insertionIndex = min(index1, index2)
        currentList.add(insertionIndex, newFolder)

        _uiState.update { it.copy(kioskItems = currentList) }
    }

    private fun saveLayout() {
        viewModelScope.launch {
            val json = gson.toJson(_uiState.value.kioskItems)
            settingsRepository.setKioskLayoutJson(json)
            Log.d("KioskViewModel", "Kiosk layout saved.")
        }
    }

    private fun handleActionButton(action: KioskActionButton) {
        viewModelScope.launch {
            when (action) {
                KioskActionButton.FLASHLIGHT -> _sideEffect.emit(KioskSideEffect.ToggleFlashlight(true))
                KioskActionButton.WIFI -> _sideEffect.emit(KioskSideEffect.OpenWifiSettings)
                KioskActionButton.BLUETOOTH -> _sideEffect.emit(KioskSideEffect.OpenBluetoothSettings)
                KioskActionButton.LOCATION -> _sideEffect.emit(KioskSideEffect.OpenLocationSettings)
                KioskActionButton.VOLUME -> _sideEffect.emit(KioskSideEffect.ShowVolumeSlider)
                KioskActionButton.SCREEN_ROTATION -> _sideEffect.emit(KioskSideEffect.ToggleScreenRotation)
            }
        }
    }

    private fun verifyPasswordAndNavigate(password: String) {
        viewModelScope.launch {
            if (passwordManager.verifyPassword(password)) {
                _sideEffect.emit(KioskSideEffect.NavigateToSettings)
            } else {
                _sideEffect.emit(KioskSideEffect.ShowToast("סיסמה שגויה"))
            }
        }
    }
}