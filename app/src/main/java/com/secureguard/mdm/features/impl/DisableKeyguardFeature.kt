package com.secureguard.mdm.features.impl

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.secureguard.mdm.R
import com.secureguard.mdm.features.api.ProtectionFeature

object DisableKeyguardFeature : ProtectionFeature {
    override val id: String = "disable_keyguard"
    override val titleRes: Int = R.string.feature_disable_keyguard_title
    override val descriptionRes: Int = R.string.feature_disable_keyguard_description
    override val iconRes: Int = R.drawable.ic_lock_open

    @SuppressLint("NewApi")
    override fun applyPolicy(context: Context, dpm: DevicePolicyManager, admin: ComponentName, enable: Boolean) {
        dpm.setKeyguardDisabled(admin, enable)
        // There is no reliable cross-version getter for this, so we must persist the state.
        context.getSharedPreferences("secure_guard_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(id, enable).apply()
    }

    override fun isPolicyActive(context: Context, dpm: DevicePolicyManager, admin: ComponentName): Boolean {
        // Rely on our stored preference as the system getter is not reliable across versions.
        return context.getSharedPreferences("secure_guard_prefs", Context.MODE_PRIVATE)
            .getBoolean(id, false)
    }
}