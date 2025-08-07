package com.secureguard.mdm.appblocker

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.mdm.R
import com.secureguard.mdm.SecureGuardDeviceAdminReceiver
import com.secureguard.mdm.data.db.BlockedAppCache
import com.secureguard.mdm.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

private const val TAG = "AppBlockerViewModel"

@HiltViewModel
class AppBlockerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dpm: DevicePolicyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppBlockerUiState())
    val uiState = _uiState.asStateFlow()

    private var allAppsMasterList: List<AppInfo> = emptyList()
    private var allBlockedAppsMasterList: List<AppInfo> = emptyList()

    private val adminComponentName by lazy { SecureGuardDeviceAdminReceiver.getComponentName(context) }
    private val iconsDir by lazy { File(context.filesDir, "app_icons").apply { mkdirs() } }

    init {
        loadAllData()
    }

    fun onEvent(event: AppBlockerEvent) {
        when (event) {
            is AppBlockerEvent.OnFilterChanged -> onFilterChanged(event.newFilter)
            is AppBlockerEvent.OnAppSelectionChanged -> onAppSelectionChanged(event.packageName, event.isBlocked)
            is AppBlockerEvent.OnSaveRequest -> saveAndApplyChanges() // קריאה ישירה
            is AppBlockerEvent.OnUnblockSelectedRequest -> unblockSelectedApps() // קריאה ישירה
            is AppBlockerEvent.OnAddPackageManually -> addPackageManually(event.packageName)
            is AppBlockerEvent.OnToggleUnblockSelection -> toggleUnblockSelection(event.packageName)
            is AppBlockerEvent.OnSearchQueryChanged -> onSearchQueryChanged(event.query)
            is AppBlockerEvent.OnDismissPasswordPrompt -> { /* No longer needed */ }
        }
    }

    fun loadAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchQuery = "") }
            allAppsMasterList = getInstalledApps()
            allBlockedAppsMasterList = getBlockedAppsFromCache()
            applyFilter()
            applyBlockedAppsFilter()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun onFilterChanged(newFilter: AppFilterType) {
        if (newFilter != _uiState.value.currentFilter) {
            _uiState.update { it.copy(currentFilter = newFilter) }
            applyFilter()
        }
    }

    private fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
        applyBlockedAppsFilter()
    }

    private fun applyFilter() {
        val query = _uiState.value.searchQuery.lowercase()
        var filteredList = when (_uiState.value.currentFilter) {
            AppFilterType.USER_ONLY -> allAppsMasterList.filter { it.isInstalled && !it.isSystemApp }
            AppFilterType.ALL_EXCEPT_CORE -> allAppsMasterList.filter { it.isInstalled && !it.packageName.startsWith("com.android.") }
            AppFilterType.LAUNCHER_ONLY -> allAppsMasterList.filter { it.isLauncherApp }
            AppFilterType.ALL -> allAppsMasterList.filter { it.isInstalled }
        }
        if (query.isNotBlank()) {
            filteredList = filteredList.filter {
                it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
        _uiState.update { it.copy(displayedAppsForSelection = filteredList) }
    }

    private fun applyBlockedAppsFilter() {
        val query = _uiState.value.searchQuery.lowercase()
        val filteredList = if(query.isNotBlank()) {
            allBlockedAppsMasterList.filter {
                it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        } else {
            allBlockedAppsMasterList
        }
        _uiState.update { it.copy(displayedBlockedApps = filteredList) }
    }

    private suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherPackages = pm.queryIntentActivities(launcherIntent, 0).map { it.activityInfo.packageName }.toSet()

        val allInstalledAppsInfo = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val blockedAppPackages = settingsRepository.getBlockedAppPackages()
        allInstalledAppsInfo.map { appInfo ->
            AppInfo(
                appName = appInfo.loadLabel(pm).toString(), packageName = appInfo.packageName,
                icon = appInfo.loadIcon(pm), isBlocked = blockedAppPackages.contains(appInfo.packageName),
                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isLauncherApp = launcherPackages.contains(appInfo.packageName),
                isInstalled = true
            )
        }.sortedBy { it.appName.lowercase() }
    }

    private suspend fun getBlockedAppsFromCache(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherPackages = pm.queryIntentActivities(launcherIntent, 0).map { it.activityInfo.packageName }.toSet()

        val blockedPackages = settingsRepository.getBlockedAppPackages()
        val cachedApps = settingsRepository.getBlockedAppsCache().associateBy { it.packageName }
        blockedPackages.mapNotNull { packageName ->
            try {
                val appInfo = try {
                    pm.getApplicationInfo(packageName, 0)
                } catch (e: Exception) { null }

                val cachedInfo = cachedApps[packageName]
                AppInfo(
                    appName = appInfo?.loadLabel(pm)?.toString() ?: cachedInfo?.appName ?: packageName,
                    packageName = packageName,
                    icon = cachedInfo?.let { loadIconFromFile(it.iconPath) } ?: appInfo?.loadIcon(pm) ?: createDefaultIcon(),
                    isBlocked = true,
                    isSystemApp = appInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false,
                    isLauncherApp = launcherPackages.contains(packageName),
                    isInstalled = appInfo != null
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not fully resolve app info for $packageName", e)
                null
            }
        }.sortedBy { it.appName.lowercase() }
    }

    private fun onAppSelectionChanged(packageName: String, isBlocked: Boolean) {
        allAppsMasterList = allAppsMasterList.map {
            if (it.packageName == packageName) it.copy(isBlocked = isBlocked) else it
        }
        applyFilter()
    }

    private fun saveAndApplyChanges() {
        viewModelScope.launch(Dispatchers.IO) {
            val oldBlockedSet = settingsRepository.getBlockedAppPackages()
            val newlySelectedApps = allAppsMasterList.filter { it.isBlocked }.map { it.packageName }.toSet()
            val finalBlockedSet = oldBlockedSet + newlySelectedApps

            if (finalBlockedSet == oldBlockedSet) {
                Log.d(TAG, "No changes to save.")
                return@launch
            }

            settingsRepository.setBlockedAppPackages(finalBlockedSet)
            val appsToCache = allAppsMasterList.filter { finalBlockedSet.contains(it.packageName) && !oldBlockedSet.contains(it.packageName) }
            appsToCache.forEach { cacheAppInfo(it) }

            newlySelectedApps.forEach { packageName ->
                try {
                    dpm.setApplicationHidden(adminComponentName, packageName, true)
                    Log.d(TAG, "Set hidden state for $packageName to true")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not change hidden state for $packageName", e)
                }
            }

            withContext(Dispatchers.Main) { loadAllData() }
        }
    }

    private fun toggleUnblockSelection(packageName: String) {
        val currentSelection = _uiState.value.selectionForUnblock.toMutableSet()
        if (currentSelection.contains(packageName)) currentSelection.remove(packageName) else currentSelection.add(packageName)
        _uiState.update { it.copy(selectionForUnblock = currentSelection) }
    }

    private fun unblockSelectedApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val packagesToUnblock = _uiState.value.selectionForUnblock
            if (packagesToUnblock.isEmpty()) return@launch
            val currentBlocked = settingsRepository.getBlockedAppPackages().toMutableSet()
            currentBlocked.removeAll(packagesToUnblock)
            settingsRepository.setBlockedAppPackages(currentBlocked)
            removeAppsFromCache(packagesToUnblock.toList())
            packagesToUnblock.forEach { packageName ->
                try {
                    dpm.setApplicationHidden(adminComponentName, packageName, false)
                    Log.d(TAG, "Set hidden state for $packageName to false (unblocked)")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not unhide $packageName", e)
                }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(selectionForUnblock = emptySet()) }
                loadAllData()
            }
        }
    }

    fun addPackageManually(packageName: String): String? {
        if (packageName.isBlank()) return "שם החבילה לא יכול להיות ריק."
        viewModelScope.launch {
            val currentlyBlockedPackages = settingsRepository.getBlockedAppPackages()
            allAppsMasterList = allAppsMasterList.map {
                it.copy(isBlocked = currentlyBlockedPackages.contains(it.packageName))
            }
        }

        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appAlreadyInList = allAppsMasterList.any { it.packageName == packageName }

            val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val isLauncher = pm.queryIntentActivities(launcherIntent, 0).any { it.activityInfo.packageName == packageName }

            if (!appAlreadyInList) {
                val newApp = AppInfo(
                    appName = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm),
                    isBlocked = true,
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isLauncherApp = isLauncher,
                    isInstalled = true
                )
                allAppsMasterList = (allAppsMasterList + newApp).sortedBy { it.appName.lowercase() }
            }

            onAppSelectionChanged(packageName, true)
            return null
        } catch (e: PackageManager.NameNotFoundException) {
            return "החבילה '$packageName' לא נמצאה במכשיר."
        }
    }

    private suspend fun cacheAppInfo(appInfo: AppInfo) {
        if (!appInfo.isInstalled) return
        val iconPath = saveIconToFile(appInfo.packageName, appInfo.icon) ?: return
        val cacheEntry = BlockedAppCache(
            packageName = appInfo.packageName, appName = appInfo.appName, iconPath = iconPath
        )
        settingsRepository.addAppToCache(cacheEntry)
    }

    private suspend fun removeAppsFromCache(packageNames: List<String>) {
        settingsRepository.removeAppsFromCache(packageNames)
        packageNames.forEach {
            try {
                File(iconsDir, "$it.png").delete()
            } catch (e: Exception) { Log.e(TAG, "Failed to delete icon for $it") }
        }
    }

    private fun saveIconToFile(packageName: String, drawable: Drawable): String? {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val intrinsicWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
            val intrinsicHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
            try {
                val bmp = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create bitmap from drawable for $packageName", e)
                return null
            }
        }

        try {
            val file = File(iconsDir, "$packageName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                return file.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving icon to file for $packageName", e)
        }
        return null
    }

    private fun loadIconFromFile(path: String): Drawable? = Drawable.createFromPath(path)
    private fun createDefaultIcon(): Drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
}