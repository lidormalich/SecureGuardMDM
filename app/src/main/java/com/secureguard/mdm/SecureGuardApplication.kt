package com.secureguard.mdm

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * קלאס האפליקציה הבסיסי.
 * ההערה @HiltAndroidApp מפעילה את יצירת הקוד של Hilt
 * ומאפשרת הזרקת תלויות בכל רכיבי האפליקציה.
 */
@HiltAndroidApp
class SecureGuardApplication : Application()