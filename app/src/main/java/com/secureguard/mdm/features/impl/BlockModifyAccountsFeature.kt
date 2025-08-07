package com.secureguard.mdm.features.impl

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.secureguard.mdm.R
import com.secureguard.mdm.features.api.ProtectionFeature

object BlockModifyAccountsFeature : ProtectionFeature {
    override val id: String = "block_modify_accounts"
    override val titleRes: Int = R.string.feature_block_modify_accounts_title
    override val descriptionRes: Int = R.string.feature_block_modify_accounts_description
    override val iconRes: Int = R.drawable.ic_modify_accounts_off
    override val requiredSdkVersion: Int = Build.VERSION_CODES.LOLLIPOP

    override fun applyPolicy(context: Context, dpm: DevicePolicyManager, admin: ComponentName, enable: Boolean) {
        if (enable) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_MODIFY_ACCOUNTS)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_MODIFY_ACCOUNTS)
        }
    }

    override fun isPolicyActive(context: Context, dpm: DevicePolicyManager, admin: ComponentName): Boolean {
        return dpm.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_MODIFY_ACCOUNTS, false)
    }
}