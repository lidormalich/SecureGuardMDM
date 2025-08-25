package com.secureguard.mdm.kiosk.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secureguard.mdm.R
import com.secureguard.mdm.kiosk.vm.KioskManagementEvent
import com.secureguard.mdm.kiosk.vm.KioskManagementSideEffect
import com.secureguard.mdm.kiosk.vm.KioskManagementViewModel
import com.secureguard.mdm.ui.components.InfoDialog
import com.secureguard.mdm.ui.navigation.Routes
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskManagementScreen(
    viewModel: KioskManagementViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showBlockLauncherDialog by remember { mutableStateOf<String?>(null) }
    var showResetLayoutDialog by remember { mutableStateOf(false) }
    var showBlockLauncherWarningDialog by remember { mutableStateOf(false) }
    var showSettingsInLockTaskWarningDialog by remember { mutableStateOf(false) }

    val isLockTaskApiAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collectLatest { effect ->
            when (effect) {
                is KioskManagementSideEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                }
                is KioskManagementSideEffect.ShowBlockLauncherDialog -> {
                    showBlockLauncherDialog = effect.launcherName
                }
                is KioskManagementSideEffect.ShowBlockLauncherWarningDialog -> {
                    showBlockLauncherWarningDialog = true
                }
                is KioskManagementSideEffect.ShowSettingsInLockTaskWarningDialog -> {
                    showSettingsInLockTaskWarningDialog = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.kiosk_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main toggle
                Card(modifier = Modifier.fillMaxWidth()) {
                    // --- THIS IS THE FIX: Modifier.clickable has been REMOVED from the Row ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.kiosk_management_enable_label),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.kiosk_management_enable_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.isKioskModeEnabled,
                            onCheckedChange = { isEnabled ->
                                viewModel.onEvent(KioskManagementEvent.OnKioskModeToggle(isEnabled))
                            }
                        )
                    }
                }

                // Settings in Lock Task Toggle
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = uiState.isKioskModeEnabled) {
                                if (isLockTaskApiAvailable) {
                                    viewModel.onEvent(KioskManagementEvent.OnSettingsInLockTaskToggle(!uiState.isSettingsInLockTaskEnabled))
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        //תכונה עתידית
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Task Lock - תכונה עתידית",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (!isLockTaskApiAvailable) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "תכונה זו לא זמינה!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isSettingsInLockTaskEnabled,
                            onCheckedChange = { isEnabled ->
                                viewModel.onEvent(KioskManagementEvent.OnSettingsInLockTaskToggle(isEnabled))
                            },
                            enabled = uiState.isKioskModeEnabled && isLockTaskApiAvailable
                        )
                    }
                }

                // Launcher Blocker Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = uiState.isKioskModeEnabled) {
                                viewModel.onEvent(KioskManagementEvent.OnBlockLauncherToggle(!uiState.isLauncherBlocked))
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "חסימת מסך הבית (${uiState.currentLauncherName ?: "לא זוהה"})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Switch(
                            checked = uiState.isLauncherBlocked,
                            onCheckedChange = { shouldBlock ->
                                viewModel.onEvent(KioskManagementEvent.OnBlockLauncherToggle(shouldBlock))
                            },
                            enabled = uiState.isKioskModeEnabled
                        )
                    }
                }

                // Navigation and Reset buttons
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationItem(
                            title = stringResource(id = R.string.kiosk_management_select_apps),
                            onClick = { onNavigateTo(Routes.KIOSK_APP_SELECTION) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavigationItem(
                            title = stringResource(id = R.string.kiosk_management_customize),
                            onClick = { onNavigateTo(Routes.KIOSK_CUSTOMIZATION) }
                        )
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showResetLayoutDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "איפוס פריסת האפליקציות",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showBlockLauncherDialog?.let { launcherName ->
            InfoDialog(
                title = stringResource(id = R.string.kiosk_launcher_warning_dialog_title),
                message = stringResource(id = R.string.kiosk_launcher_warning_dialog_message, launcherName),
                onDismiss = {
                    showBlockLauncherDialog = null
                    viewModel.onEvent(KioskManagementEvent.OnBlockLauncherDialogResponse(false))
                }
            )
        } != null) {}

    if (showBlockLauncherWarningDialog) {
        InfoDialog(
            title = "אזהרה חמורה!",
            message = "\n תכונה זאת לא יושמה במלואה! פעל בזהירות!",
            onDismiss = { showBlockLauncherWarningDialog = false },
            onConfirm = {
                viewModel.onEvent(KioskManagementEvent.OnBlockLauncherConfirmed)
                showBlockLauncherWarningDialog = false
            },
            isDestructive = true
        )
    }

    if (showSettingsInLockTaskWarningDialog) {
        InfoDialog(
            title = "האם אתה בטוח?",
            message = "תכונה זאת הינה חשובה כדי שההגדרות יוכלו להיפתח ממצב קיוסק, האם אתה בטוח שברצונך להמשיך?",
            onDismiss = { showSettingsInLockTaskWarningDialog = false },
            onConfirm = {
                viewModel.updateSettingsInLockTask(false)
                showSettingsInLockTaskWarningDialog = false
            }
        )
    }

    if (showResetLayoutDialog) {
        InfoDialog(
            title = "איפוס פריסת קיוסק",
            message = "מחיקה של ה-JSON מיועדת למקרי קיצון בלבד אם הקיוסק נתקע או אם אתם רוצים לשנות משהו שלא עובד בשינוי פשוט. האם להמשיך?",
            onDismiss = { showResetLayoutDialog = false },
            onConfirm = {
                viewModel.onEvent(KioskManagementEvent.OnResetKioskLayout)
                showResetLayoutDialog = false
            },
            isDestructive = true
        )
    }
}

@Composable
private fun SettingsNavigationItem(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}