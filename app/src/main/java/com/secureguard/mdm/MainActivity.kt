package com.secureguard.mdm

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect // <-- FIX
import androidx.compose.runtime.getValue      // <-- FIX
import androidx.compose.runtime.mutableStateOf  // <-- FIX
import androidx.compose.runtime.remember      // <-- FIX
import androidx.compose.runtime.setValue      // <-- FIX
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.secureguard.mdm.data.repository.SettingsRepository
import com.secureguard.mdm.kiosk.ui.KioskActivity
import com.secureguard.mdm.ui.navigation.AppNavigation
import com.secureguard.mdm.ui.theme.SecureGuardTheme
import com.secureguard.mdm.utils.SecureUpdateHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var secureUpdateHelper: SecureUpdateHelper

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var dpm: DevicePolicyManager

    private val writeSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d("MainActivity", "Returned from WRITE_SETTINGS screen.")
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "FOREGROUND_SERVICE_SPECIAL_USE permission granted.")
            } else {
                Log.w("MainActivity", "FOREGROUND_SERVICE_SPECIAL_USE permission was denied.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val startDestinationOverride = intent.getStringExtra("start_destination")
            if (startDestinationOverride == null && settingsRepository.isKioskModeEnabled()) {
                val kioskIntent = Intent(this@MainActivity, KioskActivity::class.java)
                startActivity(kioskIntent)
                finish()
                return@launch
            }

            if (!secureUpdateHelper.coreComponentExists()) {
                throw RuntimeException("Core validation component is missing or corrupted. Halting execution.")
            }

            requestSpecialUsePermission()

            setContent {
                SecureGuardTheme {
                    // --- NEW: State to control the dialog visibility ---
                    var showWriteSettingsDialog by remember { mutableStateOf(false) }

                    // --- NEW: Check for permission and update state ---
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!Settings.System.canWrite(this@MainActivity) && dpm.isDeviceOwnerApp(packageName)) {
                                showWriteSettingsDialog = true
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(startDestinationOverride = startDestinationOverride)

                        // --- NEW: Composable Dialog ---
                        if (showWriteSettingsDialog) {
                            WriteSettingsPermissionDialog(
                                onDismiss = { showWriteSettingsDialog = false },
                                onConfirm = {
                                    showWriteSettingsDialog = false
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                        Uri.parse("package:$packageName")
                                    )
                                    writeSettingsLauncher.launch(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestSpecialUsePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val permission = Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("MainActivity", "Special use foreground service permission already granted.")
                }
                else -> {
                    Log.d("MainActivity", "Requesting special use foreground service permission.")
                    requestPermissionLauncher.launch(permission)
                }
            }
        }
    }
}

// --- NEW: Composable function for the permission dialog ---
@Composable
private fun WriteSettingsPermissionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("נדרשת הרשאה נוספת") },
        text = { Text("כדי שכפתורי ה-Wi-Fi, הבלוטות' וסיבוב המסך יעבדו ממצב קיוסק, יש להעניק לאפליקציה הרשאה לשנות הגדרות מערכת.\n\nאם לא תאשר, רק הפונקציונליות של כפתורים אלו תהיה מוגבלת.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("הענק הרשאה")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("לא עכשיו")
            }
        }
    )
}