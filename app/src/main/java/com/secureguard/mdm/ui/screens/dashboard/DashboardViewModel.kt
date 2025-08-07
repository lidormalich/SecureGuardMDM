package com.secureguard.mdm.ui.screens.dashboard

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.mdm.SecureGuardDeviceAdminReceiver
import com.secureguard.mdm.features.api.ProtectionFeature
import com.secureguard.mdm.features.registry.FeatureRegistry
import com.secureguard.mdm.receivers.InstallReceiver
import com.secureguard.mdm.security.PasswordManager
import com.secureguard.mdm.utils.SecureUpdateHelper
import com.secureguard.mdm.utils.UpdateVerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeatureStatus(val feature: ProtectionFeature, val isActive: Boolean)
data class DashboardUiState(val activeFeatures: List<FeatureStatus> = emptyList(), val isLoading: Boolean = true, val isPasswordPromptVisible: Boolean = false, val passwordError: String? = null)
sealed class DashboardEvent { object OnSettingsClicked : DashboardEvent(); data class OnPasswordEntered(val password: String) : DashboardEvent(); object OnDismissPasswordPrompt : DashboardEvent(); data class OnUpdateFileSelected(val uri: Uri?) : DashboardEvent() }
sealed class DashboardSideEffect { data class ToastMessage(val message: String) : DashboardSideEffect() }

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val passwordManager: PasswordManager,
    private val secureUpdateHelper: SecureUpdateHelper,
    private val dpm: DevicePolicyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Unit>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val _sideEffect = MutableSharedFlow<DashboardSideEffect>()
    val sideEffect = _sideEffect.asSharedFlow()

    init {
        loadFeatureStatuses()
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.OnSettingsClicked -> _uiState.update { it.copy(isPasswordPromptVisible = true, passwordError = null) }
            is DashboardEvent.OnDismissPasswordPrompt -> _uiState.update { it.copy(isPasswordPromptVisible = false) }
            is DashboardEvent.OnPasswordEntered -> verifyPasswordAndNavigate(event.password)
            is DashboardEvent.OnUpdateFileSelected -> event.uri?.let { handleSecureUpdate(it) }
        }
    }

    private fun handleSecureUpdate(uri: Uri) {
        viewModelScope.launch {
            when (val result = secureUpdateHelper.verifyUpdate(uri)) {
                is UpdateVerificationResult.Success -> installPackage(uri)
                is UpdateVerificationResult.Failure -> _sideEffect.emit(DashboardSideEffect.ToastMessage(result.errorMessage))
            }
        }
    }

    private fun installPackage(apkUri: Uri) {
        viewModelScope.launch {
            try {
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)
                context.contentResolver.openInputStream(apkUri)?.use { apkStream ->
                    session.openWrite("SecureGuardUpdate", 0, -1).use { sessionStream ->
                        apkStream.copyTo(sessionStream)
                        session.fsync(sessionStream)
                    }
                }
                val intent = Intent(context, InstallReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                )
                session.commit(pendingIntent.intentSender)
                session.close()
            } catch (e: Exception) {
                _sideEffect.emit(DashboardSideEffect.ToastMessage("שגיאה בהתחלת ההתקנה: ${e.localizedMessage}"))
            }
        }
    }

    private fun verifyPasswordAndNavigate(password: String) {
        viewModelScope.launch {
            if (passwordManager.verifyPassword(password)) {
                _uiState.update { it.copy(isPasswordPromptVisible = false) }
                _navigationEvent.emit(Unit)
            } else {
                _uiState.update { it.copy(passwordError = "סיסמה שגויה") }
            }
        }
    }

    fun loadFeatureStatuses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val adminComponent = SecureGuardDeviceAdminReceiver.getComponentName(context)
            val allStatuses = FeatureRegistry.allFeatures.map { feature ->
                val isActive = feature.isPolicyActive(context, dpm, adminComponent)
                FeatureStatus(feature, isActive)
            }
            val activeFeatures = allStatuses.filter { it.isActive }
            _uiState.update { it.copy(activeFeatures = activeFeatures, isLoading = false) }
        }
    }
}