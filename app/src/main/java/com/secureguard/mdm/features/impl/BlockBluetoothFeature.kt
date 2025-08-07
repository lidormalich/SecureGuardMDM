package com.secureguard.mdm.features.impl

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.secureguard.mdm.R
import com.secureguard.mdm.features.api.ProtectionFeature

object BlockBluetoothFeature : ProtectionFeature {
    override val id = "block_bluetooth"
    override val titleRes = R.string.feature_bluetooth_title
    override val descriptionRes = R.string.feature_bluetooth_description
    override val iconRes = R.drawable.ic_bluetooth_disabled

    override fun applyPolicy(context: Context, dpm: DevicePolicyManager, admin: ComponentName, enable: Boolean) {
        if (enable) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_BLUETOOTH)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_BLUETOOTH)
        }
    }

    override fun isPolicyActive(context: Context, dpm: DevicePolicyManager, admin: ComponentName): Boolean {
        // --- הוספת בדיקת גרסה ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return dpm.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_BLUETOOTH, false)
        }
        return false
    }
}