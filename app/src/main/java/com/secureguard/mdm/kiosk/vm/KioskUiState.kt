package com.secureguard.mdm.kiosk.vm

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.secureguard.mdm.kiosk.model.KioskActionButton
import com.secureguard.mdm.kiosk.model.KioskApp
import com.secureguard.mdm.kiosk.model.KioskFolder
import com.secureguard.mdm.kiosk.model.KioskItem
import com.secureguard.mdm.utils.update.UpdateInfo

data class KioskUiState(
    val isLoading: Boolean = true,
    val kioskTitle: String = "Kiosk Mode",
    val backgroundColor: Color = Color.DarkGray,
    val primaryColor: Color = Color(0xFF6200EE.toInt()),
    val showSecureUpdate: Boolean = true,
    val kioskItems: List<KioskItem> = emptyList(),
    val actionButtons: List<KioskActionButton> = emptyList(),
    val isOfficialBuild: Boolean = false,
    val isContactEmailVisible: Boolean = true,

    // --- NEW: State for context menu and dialogs ---
    val contextMenuItemId: String? = null,
    val folderPendingRename: KioskFolder? = null
)

sealed class KioskEvent {
    // General
    object LoadData : KioskEvent()
    object SaveLayout : KioskEvent()
    object RefreshLayout : KioskEvent() // --- NEW ---

    // Item Interactions
    data class OnItemClick(val item: KioskItem) : KioskEvent()
    data class OnItemLongPress(val item: KioskItem) : KioskEvent() // --- NEW ---
    object DismissContextMenu : KioskEvent() // --- NEW ---

    // Drag and Drop
    data class OnItemsMoved(val from: Int, val to: Int) : KioskEvent()
    data class OnItemsMerged(val from: Int, val to: Int) : KioskEvent()

    // Folder Management
    data class OnCreateFolderConfirmed(val folderName: String, val app1: KioskApp, val app2: KioskApp) : KioskEvent()
    data class RequestRenameFolder(val folder: KioskFolder) : KioskEvent() // --- NEW ---
    data class OnRenameFolderConfirmed(val folderId: String, val newName: String) : KioskEvent() // --- NEW ---
    data class DisbandFolder(val folder: KioskFolder) : KioskEvent() // --- NEW ---

    // Dialogs & Settings
    object OnSettingsClick : KioskEvent()
    object OnInfoClick : KioskEvent()
    data class OnPasswordEntered(val password: String) : KioskEvent()
    data class OnActionButtonClick(val action: KioskActionButton) : KioskEvent()

    // Updates
    object OnManualUpdateCheck : KioskEvent()
    data class OnUpdateFileSelected(val uri: Uri?) : KioskEvent()
}

sealed class KioskSideEffect {
    data class LaunchApp(val packageName: String) : KioskSideEffect()
    data class ShowFolderContents(val folder: KioskFolder) : KioskSideEffect()
    data class ShowCreateFolderDialog(val app1: KioskApp, val app2: KioskApp) : KioskSideEffect()
    data class ShowRenameFolderDialog(val folder: KioskFolder) : KioskSideEffect() // --- NEW ---

    object ShowPasswordPrompt : KioskSideEffect()
    object NavigateToSettings : KioskSideEffect()
    object ShowInfoDialog : KioskSideEffect()
    data class ToggleFlashlight(val enable: Boolean) : KioskSideEffect()
    object OpenWifiSettings : KioskSideEffect()
    object OpenBluetoothSettings : KioskSideEffect()
    object OpenLocationSettings : KioskSideEffect()
    object ShowVolumeSlider : KioskSideEffect()
    object ToggleScreenRotation : KioskSideEffect()
    data class ShowToast(val message: String) : KioskSideEffect()
    data class ShowUpdateAvailable(val info: UpdateInfo) : KioskSideEffect()
    object ShowNoUpdateAvailable : KioskSideEffect()
    data class ShowUpdateCheckFailed(val message: String) : KioskSideEffect()
    data class InstallUpdate(val apkUri: Uri) : KioskSideEffect()
}