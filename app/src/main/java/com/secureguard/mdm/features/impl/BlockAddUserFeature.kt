package com.secureguard.mdm.features.impl

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.secureguard.mdm.R
import com.secureguard.mdm.features.api.ProtectionFeature

object BlockAddUserFeature : ProtectionFeature {

    override val id = "block_add_user"
    override val titleRes = R.string.feature_add_user_title
    override val descriptionRes = R.string.feature_add_user_description
    override val iconRes = R.drawable.ic_person_add_disabled

    // ההגבלה הזו קיימת מ-API 21, ולכן היא תתמוך בכל הגרסאות של האפליקציה.
    // אין צורך לציין requiredSdkVersion.

    override fun applyPolicy(context: Context, dpm: DevicePolicyManager, admin: ComponentName, enable: Boolean) {
        if (enable) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        }
    }

    override fun isPolicyActive(context: Context, dpm: DevicePolicyManager, admin: ComponentName): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return dpm.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_ADD_USER, false)
        }
        val prefs = context.getSharedPreferences("secure_guard_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(id, false)
    }
}