package com.secureguard.mdm.kiosk.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import com.secureguard.mdm.SecureGuardDeviceAdminReceiver
import com.secureguard.mdm.kiosk.ui.KioskActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class LauncherInfo(val appName: String, val packageName: String)

@Singleton
class KioskManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dpm: DevicePolicyManager
) {
    private val pm: PackageManager = context.packageManager
    private val adminComponentName: ComponentName by lazy {
        SecureGuardDeviceAdminReceiver.getComponentName(context)
    }

    private val TAG = "KioskManager"

    /**
     * Finds a suitable home screen (launcher) application to block.
     * It queries all available launchers and returns the first one that isn't this app.
     * This is more robust than relying on the current default launcher.
     * @return LauncherInfo containing the app name and package name, or null if none found.
     */
    fun getBlockableLauncherInfo(): LauncherInfo? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        // Query for all activities that can handle the HOME intent
        val launchers = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        // Find the first launcher that is not our own application
        return launchers
            .mapNotNull { it.activityInfo }
            .firstOrNull { it.packageName != context.packageName }
            ?.let {
                LauncherInfo(
                    appName = it.loadLabel(pm).toString(),
                    packageName = it.packageName
                )
            }
    }

    /**
     * Hides an application using the Device Policy Manager.
     * @param packageName The package name of the app to hide.
     * @return True if the operation was successful, false otherwise.
     */
    fun blockLauncher(packageName: String): Boolean {
        return try {
            dpm.setApplicationHidden(adminComponentName, packageName, true)
            Log.i(TAG, "Successfully blocked launcher: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to block launcher: $packageName", e)
            false
        }
    }

    /**
     * Un-hides an application using the Device Policy Manager.
     * @param packageName The package name of the app to un-hide.
     * @return True if the operation was successful, false otherwise.
     */
    fun unblockLauncher(packageName: String): Boolean {
        return try {
            dpm.setApplicationHidden(adminComponentName, packageName, false)
            Log.i(TAG, "Successfully unblocked launcher: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unblock launcher: $packageName", e)
            false
        }
    }

    /**
     * Sets or clears the KioskActivity as the persistent default home launcher.
     * This is a powerful Device Owner API.
     * @param enable True to set KioskActivity as home, false to clear the setting.
     */
    fun setKioskAsHomeLauncher(enable: Boolean) {
        try {
            if (enable) {
                val homeIntentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                val kioskActivity = ComponentName(context, KioskActivity::class.java)
                dpm.addPersistentPreferredActivity(adminComponentName, homeIntentFilter, kioskActivity)
                Log.i(TAG, "KioskActivity set as the default home launcher.")
            } else {
                dpm.clearPackagePersistentPreferredActivities(adminComponentName, context.packageName)
                Log.i(TAG, "Persistent home launcher setting cleared for this package.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set persistent preferred activity.", e)
        }
    }
}