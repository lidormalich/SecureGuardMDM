package com.secureguard.mdm.ui.screens.dashboard

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.secureguard.mdm.R
import com.secureguard.mdm.ui.components.PasswordPromptDialog
import com.secureguard.mdm.ui.screens.dashboard.update.UpdateDialog
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAppInfoDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadFeatureStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onEvent(DashboardEvent.OnUpdateFileSelected(result.data?.data))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collectLatest { onNavigateToSettings() }
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collectLatest { effect ->
            when (effect) {
                is DashboardSideEffect.ToastMessage -> Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.dashboard_title)) },
                actions = {
                    // --- כפתור עדכון ידני ---
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/vnd.android.package-archive"
                        }
                        filePickerLauncher.launch(intent)
                    }) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = stringResource(R.string.dashboard_button_update_app))
                    }
                    // --- כפתור מידע ---
                    IconButton(onClick = { showAppInfoDialog = true }) {
                        Icon(painterResource(id = R.drawable.ic_info), contentDescription = "אודות האפליקציה")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.onEvent(DashboardEvent.OnSettingsClicked) },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(stringResource(id = R.string.dashboard_button_settings))
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                if (uiState.activeFeatures.isEmpty()) {
                    Text(
                        stringResource(id = R.string.dashboard_no_active_protections),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(uiState.activeFeatures) { featureStatus ->
                            FeatureStatusRow(featureStatus = featureStatus)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (uiState.isPasswordPromptVisible) {
        PasswordPromptDialog(
            passwordError = uiState.passwordError,
            onConfirm = { password -> viewModel.onEvent(DashboardEvent.OnPasswordEntered(password)) },
            onDismiss = { viewModel.onEvent(DashboardEvent.OnDismissPasswordPrompt) }
        )
    }

    if (showAppInfoDialog) {
        AppInfoDialog(onDismiss = { showAppInfoDialog = false })
    }

    if (uiState.updateDialogState != UpdateDialogState.HIDDEN) {
        UpdateDialog(
            uiState = uiState,
            onEvent = viewModel::onEvent
        )
    }
}


@Composable
private fun FeatureStatusRow(featureStatus: FeatureStatus) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painter = painterResource(id = featureStatus.feature.iconRes), contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(id = featureStatus.feature.titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(id = featureStatus.feature.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        Spacer(modifier = Modifier.width(16.dp))
        StatusIndicator(isActive = featureStatus.isActive)
    }
}

@Composable
private fun StatusIndicator(isActive: Boolean) {
    val icon = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning
    val text = if (isActive) stringResource(id = R.string.dashboard_status_protected) else stringResource(id = R.string.dashboard_status_unprotected)
    val color = if (isActive) Color(0xFF388E3C) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(imageVector = icon, contentDescription = text, tint = color, modifier = Modifier.size(18.dp))
        Text(text = text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}