package com.secureguard.mdm.kiosk.ui

import android.app.admin.DevicePolicyManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KioskActivity : ComponentActivity() {

    @Inject
    lateinit var dpm: DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KioskScreen(
                dpm = dpm,
                packageName = packageName,
                onStartLockTask = { startLockTask() },
                onStopLockTask = {
                    // Check if we are finishing before stopping the lock task.
                    // This prevents stopping it on configuration changes (like rotation).
                    if (isFinishing) {
                        stopLockTask()
                    }
                }
            )
        }
    }

    // The onBackPressed event is now fully handled by the `BackHandler` Composable
    // inside KioskScreen.kt. Overriding it here is no longer necessary.
}