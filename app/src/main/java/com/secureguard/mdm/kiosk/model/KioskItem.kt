package com.secureguard.mdm.kiosk.model

import com.secureguard.mdm.appblocker.AppInfo

/**
 * ממשק בסיס לכל פריט במסך הקיוסק (אפליקציה או תיקייה).
 * @property id מזהה ייחודי של הפריט.
 */
sealed interface KioskItem {
    val id: String
    val type: String
}

/**
 * מייצג אפליקציה בודדת במסך הקיוסק.
 * @param appInfo מכיל את כל המידע על האפליקציה (שם, אייקון וכו').
 */
data class KioskApp(
    val appInfo: AppInfo
) : KioskItem {
    override val id: String get() = appInfo.packageName
    override val type: String = "app"
}

/**
 * מייצג תיקייה במסך הקיוסק.
 * @param id מזהה ייחודי של התיקייה (למשל, UUID).
 * @param name שם התיקייה שיוצג למשתמש.
 * @param apps רשימה של האפליקציות שנמצאות בתוך התיקייה.
 */
data class KioskFolder(
    override val id: String,
    var name: String,
    var apps: MutableList<KioskApp>
) : KioskItem {
    override val type: String = "folder"

    // --- THIS IS THE FIX ---
    // This new property exposes the list of apps as the base type KioskItem,
    // which resolves the type mismatch issue in the adapter.
    val items: List<KioskItem>
        get() = apps
}