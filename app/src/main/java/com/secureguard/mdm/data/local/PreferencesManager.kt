package com.secureguard.mdm.data.local

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(private val prefs: SharedPreferences) {

    fun saveString(key: String, value: String?) = prefs.edit().putString(key, value).apply()
    fun loadString(key: String, defaultValue: String?): String? = prefs.getString(key, defaultValue)
    fun saveBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun loadBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)
    fun saveStringSet(key: String, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()
    fun loadStringSet(key: String, defaultValue: Set<String>): Set<String> = prefs.getStringSet(key, defaultValue) ?: defaultValue

    companion object {
        const val KEY_PASSWORD_HASH = "password_hash"
        const val KEY_IS_SETUP_COMPLETE = "is_setup_complete"
        const val KEY_BLOCKED_APP_PACKAGES = "blocked_app_packages"
        const val KEY_ORIGINAL_DIALER_PACKAGE = "original_dialer_package"
        const val KEY_CUSTOM_FRP_IDS = "custom_frp_ids"
        const val KEY_AUTO_UPDATE_CHECK_ENABLED = "auto_update_check_enabled" // <-- מפתח חדש
    }
}