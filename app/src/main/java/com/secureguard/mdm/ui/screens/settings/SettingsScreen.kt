package com.secureguard.mdm.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secureguard.mdm.R
import com.secureguard.mdm.features.api.ProtectionFeature
import com.secureguard.mdm.features.impl.FrpProtectionFeature
import com.secureguard.mdm.ui.components.InfoDialog
import com.secureguard.mdm.ui.components.PasswordPromptDialog
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToAppSelection: () -> Unit,
    onNavigateToBlockedAppsDisplay: () -> Unit,
    onNavigateToFrpSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val passwordPromptState by viewModel.passwordPromptState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showUnsupportedDialogFor by remember { mutableStateOf<FeatureToggle?>(null) }
    var showRemoveConfirmationDialog by remember { mutableStateOf(false) }
    var showLockConfirmationDialog by remember { mutableStateOf(false) }
    var showInfoDialogFor by remember { mutableStateOf<FeatureToggle?>(null) }
    var showFrpWarningDialog by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onEvent(SettingsEvent.OnVpnPermissionResult(result.resultCode == Activity.RESULT_OK))
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collectLatest { effect ->
            when (effect) {
                is SettingsSideEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.vpnPermissionRequestEvent.collectLatest {
            val intent = VpnService.prepare(context)
            if (intent != null) vpnPermissionLauncher.launch(intent)
            else viewModel.onEvent(SettingsEvent.OnVpnPermissionResult(true))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiState.collectLatest { state ->
            state.snackbarMessage?.let { message ->
                snackbarHostState.showSnackbar(message)
                viewModel.onEvent(SettingsEvent.OnSnackbarShown)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.triggerUninstallEvent.collectLatest {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(SettingsEvent.OnSaveClick) }) {
                Icon(Icons.Default.Save, contentDescription = stringResource(id = R.string.settings_button_save))
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
            ) {
                item {
                    Text(
                        text = stringResource(id = R.string.category_ui_and_behavior),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsToggleItem(
                            title = stringResource(id = R.string.settings_item_toggle_position),
                            isChecked = uiState.isToggleOnStart,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.OnTogglePositionChanged(it)) }
                        )
                        SettingsToggleItem(
                            title = stringResource(id = R.string.settings_item_use_checkbox),
                            isChecked = uiState.useCheckbox,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.OnControlTypeChanged(it)) }
                        )
                        SettingsToggleItem(
                            title = stringResource(id = R.string.settings_item_show_contact_email),
                            isChecked = uiState.isContactEmailVisible,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.OnContactEmailVisibilityChanged(it)) }
                        )
                    }
                }

                uiState.categoryToggles.forEach { category ->
                    item {
                        Text(
                            text = stringResource(id = category.titleResId),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                        )
                    }
                    items(items = category.toggles, key = { it.feature.id }) { toggle ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            FeatureToggleRow(
                                toggle = toggle,
                                useCheckbox = uiState.useCheckbox,
                                isControlOnStart = uiState.isToggleOnStart,
                                onToggle = { isEnabled ->
                                    if (toggle.feature.id == FrpProtectionFeature.id && isEnabled) {
                                        showFrpWarningDialog = true
                                    } else {
                                        viewModel.onEvent(SettingsEvent.OnToggleFeature(toggle.feature.id, isEnabled))
                                    }
                                },
                                onInfoClick = { showInfoDialogFor = toggle },
                                onRowClick = { if (!toggle.isSupported) showUnsupportedDialogFor = toggle }
                            )
                        }
                    }
                }

                item {
                    Text(
                        "פעולות מתקדמות",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp))
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsActionItem("הגדר FRP מותאם אישית", R.drawable.ic_frp_shield, onNavigateToFrpSettings)
                        SettingsActionItem(stringResource(id = R.string.settings_item_change_password), R.drawable.ic_key, onNavigateToChangePassword)
                        SettingsToggleItem(
                            title = stringResource(id = R.string.settings_item_disable_all_updates),
                            isChecked = uiState.areAllUpdatesDisabled,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.OnDisableAllUpdatesChanged(it)) }
                        )
                        SettingsActionItem(stringResource(id = R.string.settings_item_lock_settings), R.drawable.ic_remove_protection, onClick = { showLockConfirmationDialog = true }, isDestructive = true)
                        SettingsActionItem(stringResource(id = R.string.settings_item_remove_protection), R.drawable.ic_uninstall_off, onClick = { showRemoveConfirmationDialog = true }, isDestructive = true)
                    }
                }
            }
        }
    }

    if (passwordPromptState.isVisible) {
        PasswordPromptDialog(
            passwordError = passwordPromptState.error,
            onConfirm = { viewModel.onPasswordPromptEvent(PasswordPromptEvent.OnPasswordEntered(it)) },
            onDismiss = { viewModel.onPasswordPromptEvent(PasswordPromptEvent.OnDismiss) }
        )
    }

    if (showRemoveConfirmationDialog) {
        InfoDialog(
            title = stringResource(id = R.string.settings_remove_protection_dialog_title),
            message = stringResource(id = R.string.settings_remove_protection_dialog_message),
            onDismiss = { showRemoveConfirmationDialog = false },
            onConfirm = {
                showRemoveConfirmationDialog = false
                viewModel.onEvent(SettingsEvent.OnRemoveProtectionRequest)
            },
            isDestructive = true
        )
    }

    if (showLockConfirmationDialog) {
        LockSettingsConfirmationDialog(
            onDismiss = { showLockConfirmationDialog = false },
            onConfirm = { allowManualUpdate ->
                showLockConfirmationDialog = false
                viewModel.onEvent(SettingsEvent.OnLockSettingsConfirmed(allowManualUpdate))
            }
        )
    }

    showUnsupportedDialogFor?.let { toggle ->
        InfoDialog(
            title = stringResource(id = R.string.dialog_title_unsupported_feature),
            message = context.getString(R.string.dialog_description_unsupported_feature, context.getString(toggle.feature.titleRes), toggle.requiredApi, getAndroidVersionName(toggle.requiredApi), Build.VERSION.SDK_INT, Build.VERSION.RELEASE),
            onDismiss = { showUnsupportedDialogFor = null }
        )
    }

    showInfoDialogFor?.let { toggle ->
        InfoDialog(
            title = stringResource(id = toggle.feature.titleRes),
            message = stringResource(id = toggle.feature.descriptionRes),
            onDismiss = { showInfoDialogFor = null }
        )
    }

    if (showFrpWarningDialog) {
        InfoDialog(
            title = stringResource(id = R.string.frp_warning_dialog_title),
            message = stringResource(id = R.string.frp_warning_dialog_message),
            onDismiss = { showFrpWarningDialog = false },
            onConfirm = {
                showFrpWarningDialog = false
                viewModel.onEvent(SettingsEvent.OnToggleFeature(FrpProtectionFeature.id, true))
            },
            isDestructive = true
        )
    }
}

@Composable
private fun LockSettingsConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var allowManualUpdate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.settings_lock_dialog_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.settings_lock_dialog_message))
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { allowManualUpdate = !allowManualUpdate }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allowManualUpdate,
                        onCheckedChange = { allowManualUpdate = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.settings_lock_dialog_allow_manual_update))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(allowManualUpdate) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(id = R.string.dialog_button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_button_cancel))
            }
        }
    )
}

@Composable
private fun FeatureToggleRow(
    toggle: FeatureToggle,
    useCheckbox: Boolean,
    isControlOnStart: Boolean,
    onToggle: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onRowClick: () -> Unit
) {
    val tint = if (toggle.isSupported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    @Composable
    fun Control() {
        if (useCheckbox) {
            Checkbox(
                checked = toggle.isEnabled,
                onCheckedChange = onToggle,
                enabled = toggle.isSupported
            )
        } else {
            Switch(
                checked = toggle.isEnabled,
                onCheckedChange = onToggle,
                enabled = toggle.isSupported
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !toggle.isSupported, onClick = onRowClick)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isControlOnStart) {
                    Control()
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Icon(
                    painter = painterResource(id = toggle.feature.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = tint
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = toggle.feature.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = tint
                    )
                }

                IconButton(onClick = onInfoClick, enabled = toggle.isSupported) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "מידע נוסף",
                        tint = tint.copy(alpha = 0.7f)
                    )
                }

                if (!isControlOnStart) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Control()
                }
            }
            toggle.conflictReasonResId?.let { reasonResId ->
                Text(
                    text = stringResource(id = reasonResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 48.dp, top = 4.dp)
                )
            }
        }
    }
}


@Composable
fun SettingsActionItem(title: String, iconRes: Int, onClick: () -> Unit, isDestructive: Boolean = false) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(24.dp), tint = color)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = color)
        }
    }
}

@Composable
fun SettingsToggleItem(title: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, iconRes: Int? = null) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!isChecked) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            iconRes?.let {
                Icon(painter = painterResource(id = it), contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = isChecked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun getAndroidVersionName(sdkInt: Int): String {
    return when (sdkInt) {
        Build.VERSION_CODES.LOLLIPOP_MR1 -> "5.1"; Build.VERSION_CODES.M -> "6.0"; Build.VERSION_CODES.N -> "7.0"; Build.VERSION_CODES.N_MR1 -> "7.1"; Build.VERSION_CODES.O -> "8.0"; Build.VERSION_CODES.O_MR1 -> "8.1"; Build.VERSION_CODES.P -> "9"; Build.VERSION_CODES.Q -> "10"; Build.VERSION_CODES.R -> "11"; Build.VERSION_CODES.S -> "12"; Build.VERSION_CODES.S_V2 -> "12L"; Build.VERSION_CODES.TIRAMISU -> "13"; 34 -> "14"; else -> sdkInt.toString()
    }
}