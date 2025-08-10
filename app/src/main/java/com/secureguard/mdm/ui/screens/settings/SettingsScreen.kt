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
    var showInfoDialogFor by remember { mutableStateOf<FeatureToggle?>(null) }
    var showFrpWarningDialog by remember { mutableStateOf(false) } // State for FRP warning

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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                uiState.categoryToggles.forEach { category ->
                    item {
                        Text(
                            text = stringResource(id = category.titleResId),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(items = category.toggles, key = { it.feature.id }) { toggle ->
                        FeatureToggleRow(
                            toggle = toggle,
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
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                item {
                    Text("ניהול אפליקציות וטלפון", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                item {
                    SettingsActionItem(stringResource(id = R.string.settings_item_select_apps_to_block), R.drawable.ic_manage_apps, onNavigateToAppSelection)
                }
                item {
                    SettingsActionItem(stringResource(id = R.string.settings_item_view_blocked_apps), R.drawable.ic_apps_blocked, onNavigateToBlockedAppsDisplay)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    item {
                        SettingsActionItem(
                            title = stringResource(R.string.settings_item_change_default_dialer),
                            iconRes = R.drawable.ic_dialpad,
                            onClick = {
                                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                item {
                    Text("פעולות נוספות", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                item {
                    SettingsToggleItem(
                        title = stringResource(id = R.string.settings_item_auto_update_check),
                        iconRes = R.drawable.ic_system_update,
                        isChecked = uiState.isAutoUpdateEnabled,
                        onCheckedChange = { isEnabled -> viewModel.onEvent(SettingsEvent.OnAutoUpdateToggled(isEnabled)) }
                    )
                }
                item {
                    SettingsActionItem(
                        title = "הגדר FRP מותאם אישית",
                        iconRes = R.drawable.ic_frp_shield,
                        onClick = onNavigateToFrpSettings
                    )
                }
                item {
                    SettingsActionItem(stringResource(id = R.string.settings_item_change_password), R.drawable.ic_key, onNavigateToChangePassword)
                }
                item {
                    SettingsActionItem(title = stringResource(id = R.string.settings_item_remove_protection), iconRes = R.drawable.ic_remove_protection, onClick = { showRemoveConfirmationDialog = true }, isDestructive = true)
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

    showUnsupportedDialogFor?.let { toggle ->
        val context = LocalContext.current
        InfoDialog(
            title = stringResource(id = R.string.dialog_title_unsupported_feature),
            message = context.getString(R.string.dialog_description_unsupported_feature, context.getString(toggle.feature.titleRes), toggle.requiredApi, getAndroidVersionName(toggle.requiredApi), Build.VERSION.SDK_INT, Build.VERSION.RELEASE),
            onDismiss = { showUnsupportedDialogFor = null }
        )
    }

    showInfoDialogFor?.let { toggle ->
        val feature: ProtectionFeature = toggle.feature
        InfoDialog(
            title = stringResource(id = feature.titleRes),
            message = stringResource(id = feature.descriptionRes),
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
private fun FeatureToggleRow(
    toggle: FeatureToggle,
    onToggle: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onRowClick: () -> Unit
) {
    val tint = if (toggle.isSupported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !toggle.isSupported, onClick = onRowClick)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = toggle.feature.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = tint
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(id = toggle.feature.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = tint
                        )
                        if (toggle.feature.id == FrpProtectionFeature.id) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.feature_frp_protection_experimental_tag),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                IconButton(onClick = onInfoClick, enabled = toggle.isSupported) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "מידע נוסף",
                        tint = tint.copy(alpha = 0.7f)
                    )
                }

                Switch(
                    checked = toggle.isEnabled,
                    onCheckedChange = onToggle,
                    enabled = toggle.isSupported
                )
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
fun SettingsToggleItem(title: String, iconRes: Int, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!isChecked) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = isChecked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun getAndroidVersionName(sdkInt: Int): String {
    return when (sdkInt) {
        Build.VERSION_CODES.LOLLIPOP_MR1 -> "5.1"
        Build.VERSION_CODES.M -> "6.0"
        Build.VERSION_CODES.N -> "7.0"
        Build.VERSION_CODES.N_MR1 -> "7.1"
        Build.VERSION_CODES.O -> "8.0"
        Build.VERSION_CODES.O_MR1 -> "8.1"
        Build.VERSION_CODES.P -> "9"
        Build.VERSION_CODES.Q -> "10"
        Build.VERSION_CODES.R -> "11"
        Build.VERSION_CODES.S -> "12"
        Build.VERSION_CODES.S_V2 -> "12L"
        Build.VERSION_CODES.TIRAMISU -> "13"
        34 -> "14"
        else -> sdkInt.toString()
    }
}