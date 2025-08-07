package com.secureguard.mdm.features.registry

import com.secureguard.mdm.features.api.ProtectionFeature
import com.secureguard.mdm.features.impl.*

/**
 * אובייקט סינגלטון המשמש כמרשם מרכזי לכל חסימות ההגנה באפליקציה.
 * המטרה היא לרכז את כל החסימות הזמינות במקום אחד.
 */
object FeatureRegistry {

    /**
     * רשימה קבועה של כל מופעי החסימות.
     * כדי להוסיף חסימה חדשה לאפליקציה, יש להוסיף אותה לרשימה זו.
     */
    val allFeatures: List<ProtectionFeature> = listOf(
        BlockDeveloperOptionsFeature,
        BlockBluetoothFeature,
        BlockUnknownSourcesFeature,
        BlockWifiFeature,
        BlockAddUserFeature,
        BlockCameraFeature,
        BlockScreenshotFeature,
        BlockUsbFileTransferFeature,
        BlockMicrophoneFeature,
        BlockLocationSharingFeature,
        BlockBluetoothSharingFeature,
        BlockMobileDataFeature,
        BlockTetheringFeature,
        BlockPlayStoreFeature,
        BlockFactoryResetFeature,
        BlockOutgoingCallsFeature,
        BlockSafeBootFeature,
        BlockInternetVpnFeature,
        BlockSmsFeature,
        BlockInstallAppsFeature,
        BlockRemoveUserFeature,
        BlockModifyAccountsFeature,
        BlockRemoveManagedProfileFeature,
        BlockSetUserIconFeature,
        BlockAdjustVolumeFeature,
        BlockSetWallpaperFeature,
        DisableStatusBarFeature,
        BlockAutofillFeature,
        BlockAmbientDisplayFeature,
        BlockAppsControlFeature,
        BlockUninstallAppsFeature,
        BlockMountPhysicalMediaFeature,
        DisableKeyguardFeature,
        BlockConfigLocationFeature,
        BlockConfigCredentialsFeature,
        BlockPrintingFeature,
        BlockConfigCellBroadcastsFeature,
        BlockContentCaptureFeature,
        BlockSystemErrorDialogsFeature,
        BlockPrivateDnsFeature,
        BlockIncomingCallsFeature,
        BlockPasswordChangeFeature,
        BlockVpnSettingsFeature,
        InstallAndProtectNetGuardFeature,
        ForceNetGuardVpnFeature
    )
}