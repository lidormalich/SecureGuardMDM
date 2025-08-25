package com.secureguard.mdm.kiosk.ui

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secureguard.mdm.MainActivity
import com.secureguard.mdm.R
import com.secureguard.mdm.kiosk.model.KioskApp
import com.secureguard.mdm.kiosk.model.KioskFolder
import com.secureguard.mdm.kiosk.model.KioskItem
import com.secureguard.mdm.kiosk.vm.KioskEvent
import com.secureguard.mdm.kiosk.vm.KioskSideEffect
import com.secureguard.mdm.kiosk.vm.KioskViewModel
import com.secureguard.mdm.ui.theme.SecureGuardTheme
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

@Composable
fun KioskScreen(
    viewModel: KioskViewModel = hiltViewModel(),
    dpm: android.app.admin.DevicePolicyManager,
    packageName: String,
    onStartLockTask: () -> Unit,
    onStopLockTask: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isFlashlightOn by remember { mutableStateOf(false) }

    // --- State for Dialogs ---
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf<KioskFolder?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf<Pair<KioskApp, KioskApp>?>(null) }
    var showUpdateDialog by remember { mutableStateOf<com.secureguard.mdm.utils.update.UpdateInfo?>(null) }
    var showRenameFolderDialog by remember { mutableStateOf<KioskFolder?>(null) }


    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                viewModel.onEvent(KioskEvent.OnUpdateFileSelected(result.data?.data))
            }
        }
    )

    // --- Side Effects Handler ---
    LaunchedEffect(key1 = true) {
        viewModel.sideEffect.collectLatest { effect ->
            when (effect) {
                is KioskSideEffect.LaunchApp -> launchApp(context, effect.packageName)
                is KioskSideEffect.ShowFolderContents -> showFolderDialog = effect.folder
                is KioskSideEffect.ShowCreateFolderDialog -> showCreateFolderDialog = effect.app1 to effect.app2
                is KioskSideEffect.ShowRenameFolderDialog -> showRenameFolderDialog = effect.folder
                is KioskSideEffect.ShowPasswordPrompt -> showPasswordPrompt = true
                is KioskSideEffect.NavigateToSettings -> navigateToSettings(context)
                is KioskSideEffect.ShowInfoDialog -> showInfoDialog = true
                is KioskSideEffect.ToggleFlashlight -> {
                    toggleFlashlight(context, effect.enable)
                    isFlashlightOn = effect.enable
                }
                is KioskSideEffect.OpenWifiSettings -> openSystemSettings(context, Settings.ACTION_WIFI_SETTINGS)
                is KioskSideEffect.OpenBluetoothSettings -> openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
                is KioskSideEffect.OpenLocationSettings -> openSystemSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                is KioskSideEffect.ShowVolumeSlider -> showVolumeSlider(context)
                is KioskSideEffect.ToggleScreenRotation -> toggleRotationLock(context)
                is KioskSideEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                is KioskSideEffect.ShowUpdateAvailable -> showUpdateDialog = effect.info
                is KioskSideEffect.InstallUpdate -> installPackage(context, effect.apkUri)
                is KioskSideEffect.ShowNoUpdateAvailable -> Toast.makeText(context, context.getString(R.string.update_check_no_update), Toast.LENGTH_SHORT).show()
                is KioskSideEffect.ShowUpdateCheckFailed -> Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Lifecycle Observer ---
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onEvent(KioskEvent.LoadData)
                    if (dpm.isLockTaskPermitted(packageName)) {
                        onStartLockTask()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (isFlashlightOn) {
                        toggleFlashlight(context, false)
                        isFlashlightOn = false
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onEvent(KioskEvent.SaveLayout)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onStopLockTask()
        }
    }

    // --- Prevent exiting kiosk mode ---
    BackHandler {
        // Intercept back press
    }

    SecureGuardTheme {
        val primaryColor = uiState.primaryColor
        val onPrimaryColor = if (ColorUtils.calculateLuminance(primaryColor.toArgb()) < 0.5) Color.White else Color.Black

        Scaffold(
            topBar = {
                KioskTopAppBar(
                    title = uiState.kioskTitle,
                    backgroundColor = primaryColor,
                    contentColor = onPrimaryColor,
                    onRefresh = { viewModel.onEvent(KioskEvent.RefreshLayout) }
                )
            },
            bottomBar = {
                KioskBottomAppBar(
                    backgroundColor = primaryColor,
                    contentColor = onPrimaryColor,
                    showSecureUpdate = uiState.showSecureUpdate,
                    actionButtons = uiState.actionButtons,
                    onSettingsClick = { viewModel.onEvent(KioskEvent.OnSettingsClick) },
                    onInfoClick = { viewModel.onEvent(KioskEvent.OnInfoClick) },
                    onSecureUpdateClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/vnd.android.package-archive"
                        }
                        filePickerLauncher.launch(intent)
                    },
                    onActionButtonClick = { action ->
                        viewModel.onEvent(KioskEvent.OnActionButtonClick(action))
                    }
                )
            },
            containerColor = uiState.backgroundColor
        ) { paddingValues ->
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                DraggableKioskGrid(
                    items = uiState.kioskItems,
                    modifier = Modifier.padding(paddingValues),
                    onMove = { from, to -> viewModel.onEvent(KioskEvent.OnItemsMoved(from, to)) },
                    onMerge = { from, to -> viewModel.onEvent(KioskEvent.OnItemsMerged(from, to)) },
                    onItemClick = { item -> viewModel.onEvent(KioskEvent.OnItemClick(item)) }
                )
            }
        }
    }

    // --- Dialogs ---
    if (showPasswordPrompt) {
        PasswordPromptDialog(
            onDismiss = { showPasswordPrompt = false },
            onConfirm = { password ->
                viewModel.onEvent(KioskEvent.OnPasswordEntered(password))
                showPasswordPrompt = false
            }
        )
    }

    showRenameFolderDialog?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onDismiss = { showRenameFolderDialog = null },
            onConfirm = { newName ->
                viewModel.onEvent(KioskEvent.OnRenameFolderConfirmed(folder.id, newName))
                showRenameFolderDialog = null
            }
        )
    }

    if (showInfoDialog) {
        InfoDialog(
            uiState = uiState,
            onDismiss = { showInfoDialog = false },
            onCheckForUpdates = {
                viewModel.onEvent(KioskEvent.OnManualUpdateCheck)
                showInfoDialog = false
            }
        )
    }

    showFolderDialog?.let { folder ->
        FolderContentsDialog(
            folder = folder,
            onDismiss = { showFolderDialog = null },
            onAppClick = { app ->
                viewModel.onEvent(KioskEvent.OnItemClick(app))
            },
            onRenameClick = {
                showFolderDialog = null
                viewModel.onEvent(KioskEvent.RequestRenameFolder(folder))
            },
            onDisbandClick = {
                showFolderDialog = null
                viewModel.onEvent(KioskEvent.DisbandFolder(folder))
            }
        )
    }

    showCreateFolderDialog?.let { (app1, app2) ->
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = null },
            onConfirm = { folderName ->
                viewModel.onEvent(KioskEvent.OnCreateFolderConfirmed(folderName, app1, app2))
                showCreateFolderDialog = null
            }
        )
    }

    showUpdateDialog?.let { updateInfo ->
        UpdateAvailableDialog(
            info = updateInfo,
            onDismiss = { showUpdateDialog = null },
            onDownload = {
                showUpdateDialog = null
            }
        )
    }
}


// --- Drag and Drop Grid ---

class DragState {
    var isDragging by mutableStateOf(false)
    var dragPosition by mutableStateOf(Offset.Zero)
    var dragOffset by mutableStateOf(Offset.Zero)
    var draggedItem by mutableStateOf<KioskItem?>(null)
    var initialIndex by mutableStateOf(-1)
}

@Composable
fun rememberDragState() = remember { DragState() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggableKioskGrid(
    items: List<KioskItem>,
    modifier: Modifier = Modifier,
    onMove: (from: Int, to: Int) -> Unit,
    onMerge: (from: Int, to: Int) -> Unit,
    onItemClick: (KioskItem) -> Unit
) {
    val dragState = rememberDragState()
    val gridState = rememberLazyGridState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items = items, key = { _, item -> item.id }) { index, item ->
                var initialPosition by remember { mutableStateOf(Offset.Zero) }
                val isBeingDragged = index == dragState.initialIndex && dragState.isDragging

                val alpha by animateFloatAsState(targetValue = if (isBeingDragged) 0f else 1f, label = "alpha")

                Box(modifier = Modifier
                    .onGloballyPositioned {
                        initialPosition = it.localToWindow(Offset.Zero)
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragState.initialIndex = index
                                dragState.draggedItem = item
                                dragState.dragPosition = initialPosition
                                dragState.isDragging = true
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragState.dragOffset += dragAmount
                            },
                            onDragEnd = {
                                val from = dragState.initialIndex
                                val to = findTargetIndex(gridState, dragState, from)

                                if (from != -1 && to != -1 && from != to) {
                                    val fromItem = items.getOrNull(from)
                                    val toItem = items.getOrNull(to)
                                    if (fromItem is KioskApp && (toItem is KioskApp || toItem is KioskFolder)) {
                                        onMerge(from, to)
                                    } else {
                                        onMove(from, to)
                                    }
                                }
                                dragState.isDragging = false
                                dragState.dragOffset = Offset.Zero
                            }
                        )
                    }
                    .graphicsLayer { this.alpha = alpha }
                    .animateItemPlacement()
                ){
                    KioskItemContent(item = item, onClick = { onItemClick(item) })
                }
            }
        }

        if (dragState.isDragging) {
            dragState.draggedItem?.let { item ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (dragState.dragPosition.x + dragState.dragOffset.x).roundToInt(),
                                (dragState.dragPosition.y + dragState.dragOffset.y).roundToInt()
                            )
                        }
                        .zIndex(1f)
                        .graphicsLayer {
                            val scale = 1.1f
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    KioskItemContent(item = item, onClick = { }, isDragged = true)
                }
            }
        }
    }
}

private fun findTargetIndex(gridState: LazyGridState, dragState: DragState, selfIndex: Int): Int {
    val layoutInfo = gridState.layoutInfo
    val selfItemInfo = layoutInfo.visibleItemsInfo.getOrNull(selfIndex) ?: return -1

    val currentCenter = Offset(
        dragState.dragPosition.x + dragState.dragOffset.x + (selfItemInfo.size.width / 2),
        dragState.dragPosition.y + dragState.dragOffset.y + (selfItemInfo.size.height / 2)
    )

    return layoutInfo.visibleItemsInfo.minByOrNull {
        val targetCenter = Offset(
            it.offset.x.toFloat() + it.size.width / 2,
            it.offset.y.toFloat() + it.size.height / 2
        )
        (targetCenter - currentCenter).getDistanceSquared()
    }?.index ?: -1
}

@Composable
fun KioskItemContent(item: KioskItem, onClick: (KioskItem) -> Unit, isDragged: Boolean = false) {
    val clickHandler = if (isDragged) ({}) else ({ onClick(item) })
    when (item) {
        is KioskApp -> KioskAppItem(app = item, onClick = clickHandler)
        is KioskFolder -> KioskFolderItem(folder = item, onClick = clickHandler)
    }
}

// ... (Helper Functions remain the same)
private fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Could not launch app", Toast.LENGTH_SHORT).show()
    }
}
private fun navigateToSettings(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra("start_destination", "settings")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    context.startActivity(intent)
    (context as? KioskActivity)?.finish()
}
private fun installPackage(context: Context, apkUri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to start installation: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
private fun toggleFlashlight(context: Context, enable: Boolean) {
    try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cameraManager.setTorchMode(cameraId, enable)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to control flashlight", Toast.LENGTH_SHORT).show()
    }
}
private fun openSystemSettings(context: Context, action: String) {
    context.startActivity(Intent(action))
}
private fun showVolumeSlider(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.adjustStreamVolume(
        AudioManager.STREAM_MUSIC,
        AudioManager.ADJUST_SAME,
        AudioManager.FLAG_SHOW_UI
    )
}
private fun toggleRotationLock(context: Context) {
    try {
        val isAutoRotate = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (isAutoRotate) 0 else 1)
        val message = if (isAutoRotate) "Rotation locked" else "Rotation unlocked"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, "Permission to change system settings denied", Toast.LENGTH_SHORT).show()
    }
}