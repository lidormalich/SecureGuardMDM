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
    suspend fun isAutoUpdateCheckEnabled(): Boolean // <-- פונקציה חדשה
    suspend fun setAutoUpdateCheckEnabled(isEnabled: Boolean) // <-- פונקציה חדשה

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
}