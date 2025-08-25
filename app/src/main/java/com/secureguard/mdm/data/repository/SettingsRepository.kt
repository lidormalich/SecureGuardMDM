package com.secureguard.mdm.data.repository

import com.secureguard.mdm.data.db.BlockedAppCache

interface SettingsRepository {
    // --- פעולות על הגדרות כלליות ---
    suspend fun getFeatureState(featureId: String): Boolean
    suspend fun setFeatureState(featureId: String, isActive: Boolean)
    suspend fun getPasswordHash(): String?
    suspend fun setPasswordHash(hash: String)
    suspend fun isSetupComplete(): Boolean
    suspend fun setSetupComplete(isComplete: Boolean)
    suspend fun getOriginalDialerPackage(): String?
    suspend fun setOriginalDialerPackage(packageName: String?)
    suspend fun isAutoUpdateCheckEnabled(): Boolean
    suspend fun setAutoUpdateCheckEnabled(isEnabled: Boolean)

    // --- הגדרות חדשות ---
    suspend fun isToggleOnStart(): Boolean
    suspend fun setToggleOnStart(isOnStart: Boolean)
    suspend fun useCheckbox(): Boolean
    suspend fun setUseCheckbox(useCheckbox: Boolean)
    suspend fun isContactEmailVisible(): Boolean
    suspend fun setContactEmailVisible(isVisible: Boolean)
    suspend fun areAllUpdatesDisabled(): Boolean
    suspend fun setAllUpdatesDisabled(isDisabled: Boolean)
    suspend fun isSettingsLocked(): Boolean
    suspend fun lockSettingsPermanently(allowManualUpdate: Boolean)
    suspend fun allowManualUpdateWhenLocked(): Boolean
    suspend fun isShowBootToastEnabled(): Boolean
    suspend fun setShowBootToastEnabled(isEnabled: Boolean)


    // --- פעולות FRP ---
    suspend fun getCustomFrpIds(): Set<String>
    suspend fun setCustomFrpIds(ids: Set<String>)

    // --- פעולות על רשימת שמות החבילה החסומים (מקור האמת) ---
    suspend fun getBlockedAppPackages(): Set<String>
    suspend fun setBlockedAppPackages(packageNames: Set<String>)

    // --- פעולות על מטמון פרטי האפליקציות החסומות (Room) ---
    suspend fun getBlockedAppsCache(): List<BlockedAppCache>
    suspend fun addAppToCache(appCache: BlockedAppCache)
    suspend fun removeAppsFromCache(packageNames: List<String>)

    // --- Kiosk Mode Settings ---
    suspend fun isKioskModeEnabled(): Boolean
    suspend fun setKioskModeEnabled(isEnabled: Boolean)
    suspend fun getKioskAppPackages(): Set<String>
    suspend fun setKioskAppPackages(packageNames: Set<String>)
    suspend fun getKioskBlockedLauncherPackage(): String?
    suspend fun setKioskBlockedLauncherPackage(packageName: String?)
    suspend fun getKioskTitle(): String
    suspend fun setKioskTitle(title: String)
    suspend fun getKioskBackgroundColor(): Int
    suspend fun setKioskBackgroundColor(color: Int)
    // --- הוספה: פונקציות לניהול צבע המבטא (Primary) של הקיוסק ---
    suspend fun getKioskPrimaryColor(): Int
    suspend fun setKioskPrimaryColor(color: Int)
    // ----------------------------------------------------------------
    suspend fun shouldShowKioskSecureUpdate(): Boolean
    suspend fun setShouldShowKioskSecureUpdate(shouldShow: Boolean)
    suspend fun getKioskActionButtons(): Set<String>
    suspend fun setKioskActionButtons(buttons: Set<String>)
    // New methods for layout persistence
    suspend fun getKioskLayoutJson(): String?
    suspend fun setKioskLayoutJson(json: String?)
    suspend fun isKioskSettingsInLockTaskEnabled(): Boolean
    suspend fun setKioskSettingsInLockTaskEnabled(isEnabled: Boolean)

}