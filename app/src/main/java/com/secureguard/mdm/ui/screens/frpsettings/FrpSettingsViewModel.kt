package com.secureguard.mdm.ui.screens.frpsettings

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.mdm.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FrpSettingsUiState(
    val customIds: List<String> = emptyList(),
    val newIdInput: String = "",
    val isLoading: Boolean = true
)

sealed class FrpSettingsEvent {
    data class OnNewIdChanged(val value: String) : FrpSettingsEvent()
    object OnAddIdClicked : FrpSettingsEvent()
    data class OnDeleteIdClicked(val id: String) : FrpSettingsEvent()
    object OnSaveClicked : FrpSettingsEvent()
}

sealed class FrpSettingsSideEffect {
    object NavigateBack : FrpSettingsSideEffect()
    data class ShowToast(val message: String) : FrpSettingsSideEffect()
}

@HiltViewModel
class FrpSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FrpSettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _sideEffect = MutableSharedFlow<FrpSettingsSideEffect>()
    val sideEffect = _sideEffect.asSharedFlow()

    init {
        loadCustomIds()
    }

    fun onEvent(event: FrpSettingsEvent) {
        when (event) {
            is FrpSettingsEvent.OnNewIdChanged -> _uiState.update { it.copy(newIdInput = event.value) }
            is FrpSettingsEvent.OnAddIdClicked -> addId()
            is FrpSettingsEvent.OnDeleteIdClicked -> deleteId(event.id)
            is FrpSettingsEvent.OnSaveClicked -> saveCustomIds()
        }
    }

    private fun loadCustomIds() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val ids = settingsRepository.getCustomFrpIds().toList().sorted()
            _uiState.update { it.copy(customIds = ids, isLoading = false) }
        }
    }

    private fun addId() {
        val newId = _uiState.value.newIdInput.trim()
        if (newId.isNotBlank() && newId.all { it.isDigit() }) {
            if (_uiState.value.customIds.contains(newId)) {
                viewModelScope.launch { _sideEffect.emit(FrpSettingsSideEffect.ShowToast("ID כבר קיים ברשימה")) }
                return
            }
            val updatedList = (_uiState.value.customIds + newId).sorted()
            _uiState.update { it.copy(customIds = updatedList, newIdInput = "") }
        } else {
            viewModelScope.launch { _sideEffect.emit(FrpSettingsSideEffect.ShowToast("יש להזין ID המכיל מספרים בלבד")) }
        }
    }

    private fun deleteId(idToDelete: String) {
        val updatedList = _uiState.value.customIds.filter { it != idToDelete }
        _uiState.update { it.copy(customIds = updatedList) }
    }

    private fun saveCustomIds() {
        viewModelScope.launch {
            settingsRepository.setCustomFrpIds(_uiState.value.customIds.toSet())
            _sideEffect.emit(FrpSettingsSideEffect.ShowToast("רשימת ה-FRP המותאמת נשמרה"))
            _sideEffect.emit(FrpSettingsSideEffect.NavigateBack)
        }
    }
}