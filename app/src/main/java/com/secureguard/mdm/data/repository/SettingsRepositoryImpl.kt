package com.secureguard.mdm.data.repository

import com.secureguard.mdm.data.db.BlockedAppCache
import com.secureguard.mdm.data.db.BlockedAppCacheDao
import com.secureguard.mdm.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val blockedAppCacheDao: BlockedAppCacheDao
) : SettingsRepository {

    override suspend fun getFeatureState(featureId: String): Boolean = withContext(Dispatchers.IO) {
        preferencesManager.loadBoolean(featureId, false)
    }

    override suspend fun setFeatureState(featureId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        preferencesManager.saveBoolean(featureId, isActive)
    }

    override suspend fun getPasswordHash(): String? = withContext(Dispatchers.IO) {
        preferencesManager.loadString(PreferencesManager.KEY_PASSWORD_HASH, null)
    }

    override suspend fun setPasswordHash(hash: String) = withContext(Dispatchers.IO) {
        preferencesManager.saveString(PreferencesManager.KEY_PASSWORD_HASH, hash)
    }

    override suspend fun isSetupComplete(): Boolean = withContext(Dispatchers.IO) {
        preferencesManager.loadBoolean(PreferencesManager.KEY_IS_SETUP_COMPLETE, false)
    }

    override suspend fun setSetupComplete(isComplete: Boolean) = withContext(Dispatchers.IO) {
        preferencesManager.saveBoolean(PreferencesManager.KEY_IS_SETUP_COMPLETE, isComplete)
    }

    override suspend fun getOriginalDialerPackage(): String? = withContext(Dispatchers.IO) {
        preferencesManager.loadString(PreferencesManager.KEY_ORIGINAL_DIALER_PACKAGE, null)
    }

    override suspend fun setOriginalDialerPackage(packageName: String?) = withContext(Dispatchers.IO) {
        preferencesManager.saveString(PreferencesManager.KEY_ORIGINAL_DIALER_PACKAGE, packageName)
    }

    // --- מימוש פונקציות FRP ---
    override suspend fun getCustomFrpIds(): Set<String> = withContext(Dispatchers.IO) {
        preferencesManager.loadStringSet(PreferencesManager.KEY_CUSTOM_FRP_IDS, emptySet())
    }

    override suspend fun setCustomFrpIds(ids: Set<String>) = withContext(Dispatchers.IO) {
        preferencesManager.saveStringSet(PreferencesManager.KEY_CUSTOM_FRP_IDS, ids)
    }

    // --- מימוש פונקציות חסימת אפליקציות ---

    override suspend fun getBlockedAppPackages(): Set<String> = withContext(Dispatchers.IO) {
        preferencesManager.loadStringSet(PreferencesManager.KEY_BLOCKED_APP_PACKAGES, emptySet())
    }

    override suspend fun setBlockedAppPackages(packageNames: Set<String>) = withContext(Dispatchers.IO) {
        preferencesManager.saveStringSet(PreferencesManager.KEY_BLOCKED_APP_PACKAGES, packageNames)
    }

    // --- מימוש פונקציות המטמון (Room) ---

    override suspend fun getBlockedAppsCache(): List<BlockedAppCache> = withContext(Dispatchers.IO) {
        blockedAppCacheDao.getAll()
    }

    override suspend fun addAppToCache(appCache: BlockedAppCache) = withContext(Dispatchers.IO) {
        blockedAppCacheDao.insertOrUpdate(appCache)
    }

    override suspend fun removeAppsFromCache(packageNames: List<String>) = withContext(Dispatchers.IO) {
        if (packageNames.isNotEmpty()) {
            blockedAppCacheDao.deleteByPackageNames(packageNames)
        }
    }
}