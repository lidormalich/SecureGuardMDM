package com.secureguard.mdm.features.impl

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.secureguard.mdm.R
import com.secureguard.mdm.features.api.ProtectionFeature

object BlockConfigMobileNetworksFeature : ProtectionFeature {
    override val id: String = "block_config_mobile_networks"
    override val titleRes: Int = R.string.feature_block_config_mobile_networks_title
    override val descriptionRes: Int = R.string.feature_block_config_mobile_networks_description
    override val iconRes: Int = R.drawable.ic_config_mobile_networks_off

    override fun applyPolicy(context: Context, dpm: DevicePolicyManager, admin: ComponentName, enable: Boolean) {
        if (enable) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
        }
    }

    @SuppressLint("NewApi")
    override fun isPolicyActive(context: Context, dpm: DevicePolicyManager, admin: ComponentName): Boolean {
        return dpm.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, false)
    }
}