package com.secureguard.mdm.kiosk.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.secureguard.mdm.R
import com.secureguard.mdm.kiosk.model.KioskApp
import com.secureguard.mdm.kiosk.model.KioskFolder
import com.secureguard.mdm.kiosk.vm.KioskUiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskTopAppBar(
    title: String,
    backgroundColor: Color,
    contentColor: Color,
    onRefresh: () -> Unit
) {
    TopAppBar(
        title = { Text(text = title) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor,
            titleContentColor = contentColor,
            actionIconContentColor = contentColor
        ),
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Layout")
            }
            KioskTopBarContent(contentColor = contentColor)
        }
    )
}

// ... (KioskBottomAppBar, KioskTopBarContent, BatteryIndicator, KioskAppItem, KioskFolderItem remain the same)
@Composable
fun KioskBottomAppBar(
    backgroundColor: Color,
    contentColor: Color,
    showSecureUpdate: Boolean,
    actionButtons: List<com.secureguard.mdm.kiosk.model.KioskActionButton>,
    onSettingsClick: () -> Unit,
    onInfoClick: () -> Unit,
    onSecureUpdateClick: () -> Unit,
    onActionButtonClick: (com.secureguard.mdm.kiosk.model.KioskActionButton) -> Unit
) {
    BottomAppBar(
        containerColor = backgroundColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showSecureUpdate) {
                IconButton(onClick = onSecureUpdateClick) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = stringResource(R.string.kiosk_content_desc_secure_update)
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.kiosk_content_desc_main_settings)
                )
            }
            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.kiosk_content_desc_about_app)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            actionButtons.forEach { action ->
                IconButton(onClick = { onActionButtonClick(action) }) {
                    Icon(
                        painter = painterResource(id = action.iconRes),
                        contentDescription = stringResource(id = action.titleRes)
                    )
                }
            }
        }
    }
}
@Composable
fun KioskTopBarContent(contentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(end = 16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                fontSize = 20.sp,
                color = contentColor
            )
            Text(
                text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date()),
                fontSize = 12.sp,
                color = contentColor
            )
        }
        BatteryIndicator(contentColor = contentColor)
    }
}
@Composable
fun BatteryIndicator(contentColor: Color) {
    val context = LocalContext.current
    var batteryPct by remember { mutableStateOf(100) }
    val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryPct = (level * 100 / scale.toFloat()).toInt()
        }
    }
    DisposableEffect(Unit) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
        onDispose {
            context.unregisterReceiver(batteryReceiver)
        }
    }
    val iconRes = when {
        batteryPct >= 95 -> R.drawable.ic_battery_full
        batteryPct >= 80 -> R.drawable.ic_battery_6_bar
        batteryPct >= 60 -> R.drawable.ic_battery_4_bar
        batteryPct >= 40 -> R.drawable.ic_battery_3_bar
        batteryPct >= 20 -> R.drawable.ic_battery_alert
        else -> R.drawable.ic_battery_full
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$batteryPct%",
            fontSize = 14.sp,
            color = contentColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = stringResource(R.string.kiosk_content_desc_battery),
            modifier = Modifier.size(24.dp),
            tint = contentColor
        )
    }
}
@Composable
fun KioskAppItem(
    app: KioskApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .size(100.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = app.appInfo.icon),
            contentDescription = null,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.appInfo.appName,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
@Composable
fun KioskFolderItem(
    folder: KioskFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .size(100.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(folder.apps.take(4)) { app ->
                    Image(
                        painter = rememberDrawablePainter(drawable = app.appInfo.icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = folder.name,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// --- UPDATED AND NEW DIALOGS ---

@Composable
fun FolderContentsDialog(
    folder: KioskFolder,
    onDismiss: () -> Unit,
    onAppClick: (KioskApp) -> Unit,
    onRenameClick: () -> Unit,
    onDisbandClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(folder.name)
                Row {
                    IconButton(onClick = onRenameClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Folder")
                    }
                    IconButton(onClick = onDisbandClick) {
                        Icon(Icons.Default.Delete, contentDescription = "Disband Folder")
                    }
                }
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(top = 16.dp)
            ) {
                items(items = folder.items, key = { it.id }) { item ->
                    if (item is KioskApp) {
                        KioskAppItem(app = item, onClick = { onAppClick(item) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("סגור")
            }
        }
    )
}

@Composable
fun RenameFolderDialog(folder: KioskFolder, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var folderName by remember(folder.name) { mutableStateOf(folder.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שינוי שם תיקייה") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("שם התיקייה") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(folderName) }) {
                Text("שמור")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

// ... (Other dialogs: Password, CreateFolder, Info, UpdateAvailable remain the same)
@Composable
fun PasswordPromptDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("גישת מנהל") },
        text = {
            Column {
                Text("הזן את סיסמת המנהל כדי לגשת להגדרות.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("הזן סיסמה") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }) {
                Text("אישור")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}
@Composable
fun CreateFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("יצירת תיקייה חדשה") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("שם התיקייה") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(folderName) }) {
                Text("צור")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}
@Composable
fun InfoDialog(
    uiState: KioskUiState,
    onDismiss: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    val context = LocalContext.current
    val appVersion = stringResource(R.string.app_version)
    val buildStatus = stringResource(R.string.app_build_status)
    val isOfficial = buildStatus.equals("רשמית", ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.app_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text("גרסה: ", style = MaterialTheme.typography.bodyLarge)
                    Text(appVersion, style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("סטטוס: ", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = buildStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isOfficial) Color.Unspecified else MaterialTheme.colorScheme.error
                    )
                    if (!isOfficial) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isOfficial) {
                Button(onClick = onCheckForUpdates) {
                    Text(stringResource(R.string.check_for_updates))
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("סגור")
                }
                if (uiState.isContactEmailVisible) {
                    TextButton(onClick = {
                        val email = context.getString(R.string.contact_email)
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                            putExtra(Intent.EXTRA_SUBJECT, "A bloq App Inquiry")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No email app found.", Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }) {
                        Text("צור קשר")
                    }
                }
            }
        }
    )
}
@Composable
fun UpdateAvailableDialog(
    info: com.secureguard.mdm.utils.update.UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Available") },
        text = {
            Text("New version ${info.versionName} is available.\n\nChanges:\n${info.changelog}")
        },
        confirmButton = {
            Button(onClick = onDownload) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}