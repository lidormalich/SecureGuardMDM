package com.secureguard.mdm.features.impl

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import androidx.annotation.ChecksSdkIntAtLeast
import com.secureguard.mdm.R
import com.secureguard.mdm.features.api.ProtectionFeature

object BlockDeveloperOptionsFeature : ProtectionFeature {
    override val id = "block_dev_options"
    override val titleRes = R.string.feature_dev_options_title
    override val descriptionRes = R.string.feature_dev_options_description
    override val iconRes = R.drawable.ic_developer_mode
    override val requiredSdkVersion = Build.VERSION_CODES.LOLLIPOP_MR1

    override fun applyPolicy(context: Context, dpm: DevicePolicyManager, admin: ComponentName, enable: Boolean) {
        if (enable) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        }
    }

    override fun isPolicyActive(context: Context, dpm: DevicePolicyManager, admin: ComponentName): Boolean {
        // --- הוספת בדיקת גרסה ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return dpm.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES, false)
        }
        // On older versions, this specific method isn't available, but we can assume the policy isn't active.
        return false
    }
}