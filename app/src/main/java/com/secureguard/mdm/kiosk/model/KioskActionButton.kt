package com.secureguard.mdm.kiosk.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.secureguard.mdm.R

enum class KioskActionButton(
    val id: String,
    @StringRes val titleRes: Int,
    // --- הוספת השדה הזה היא התיקון ---
    @DrawableRes val iconRes: Int
) {
    FLASHLIGHT("FLASHLIGHT", R.string.kiosk_action_flashlight, R.drawable.ic_flashlight),
    BLUETOOTH("BLUETOOTH", R.string.kiosk_action_bluetooth, R.drawable.ic_bluetooth_disabled),
    WIFI("WIFI", R.string.kiosk_action_wifi, R.drawable.ic_wifi_off),
    VOLUME("VOLUME", R.string.kiosk_action_volume, R.drawable.ic_volume_up),
    LOCATION("LOCATION", R.string.kiosk_action_location, R.drawable.ic_location_off),
    SCREEN_ROTATION("SCREEN_ROTATION", R.string.kiosk_action_rotation, R.drawable.ic_screen_rotation)
}