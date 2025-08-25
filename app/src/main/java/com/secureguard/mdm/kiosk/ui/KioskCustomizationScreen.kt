package com.secureguard.mdm.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secureguard.mdm.R
import com.secureguard.mdm.kiosk.model.KioskActionButton
import com.secureguard.mdm.kiosk.vm.KioskManagementEvent
import com.secureguard.mdm.kiosk.vm.KioskManagementViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskCustomizationScreen(
    viewModel: KioskManagementViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onEvent(KioskManagementEvent.LoadKioskCustomization)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.kiosk_customization_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.onEvent(KioskManagementEvent.OnSaveKioskCustomization)
            }) {
                Icon(Icons.Default.Save, contentDescription = "שמור")
            }
        }
    ) { paddingValues ->
        if (uiState.isCustomizationLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
            ) {
                item {
                    Text(stringResource(id = R.string.kiosk_customization_general_settings), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.kioskTitle,
                        onValueChange = { viewModel.onEvent(KioskManagementEvent.OnTitleChanged(it)) },
                        label = { Text(stringResource(id = R.string.kiosk_customization_header_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // --- NEW: Advanced Color Pickers ---
                    AdvancedColorPicker(
                        label = stringResource(id = R.string.kiosk_customization_background_color),
                        selectedColor = uiState.kioskBackgroundColor,
                        onColorSelected = { viewModel.onEvent(KioskManagementEvent.OnColorSelected(it)) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    AdvancedColorPicker(
                        label = stringResource(id = R.string.kiosk_customization_accent_color),
                        selectedColor = uiState.kioskPrimaryColor,
                        onColorSelected = { viewModel.onEvent(KioskManagementEvent.OnPrimaryColorSelected(it)) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    ToggleRow(
                        label = stringResource(id = R.string.kiosk_customization_show_update_button),
                        isChecked = uiState.showSecureUpdate,
                        onCheckedChange = { viewModel.onEvent(KioskManagementEvent.OnShowSecureUpdateToggle(it)) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Text(stringResource(id = R.string.kiosk_customization_bottom_bar_actions), style = MaterialTheme.typography.titleMedium)
                }
                KioskActionButton.entries.forEach { action ->
                    item {
                        val isEnabled = action != KioskActionButton.WIFI && action != KioskActionButton.BLUETOOTH
                        ToggleRow(
                            label = stringResource(id = action.titleRes),
                            isChecked = uiState.selectedActionButtons.contains(action.id),
                            onCheckedChange = { isSelected ->
                                viewModel.onEvent(KioskManagementEvent.OnActionButtonToggle(action.id, isSelected))
                            },
                            enabled = isEnabled
                        )
                    }
                }
            }
        }
    }
}

// --- NEW: Advanced Color Picker Composable ---
@Composable
fun AdvancedColorPicker(
    label: String,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val rainbowColors = remember {
        listOf(
            Color.Red, Color(0xFFFFA500), Color.Yellow, Color.Green, Color.Blue,
            Color(0xFF4B0082), Color(0xFFEE82EE), Color.Black, Color.White, Color.Gray,
            Color(0xFF964B00), Color.Cyan
        )
    }

    // State for the text field, derived from the selected color
    var hexCode by remember(selectedColor) {
        mutableStateOf(String.format("#%08X", selectedColor.toArgb()))
    }

    val focusManager = LocalFocusManager.current

    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))

        // Pre-defined color swatches
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            modifier = Modifier.height(88.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rainbowColors) { color ->
                val isSelected = abs(color.toArgb() - selectedColor.toArgb()) < 10 // Compare with tolerance
                val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(width = 3.dp, color = borderColor, shape = CircleShape)
                        .clickable { onColorSelected(color) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Hex code input field
        OutlinedTextField(
            value = hexCode,
            onValueChange = { newHex ->
                // Allow user to type, validation happens on focus loss or enter
                hexCode = if (newHex.startsWith("#")) newHex else "#$newHex"
            },
            label = { Text("קוד הקסדצימלי (#AARRGGBB)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    try {
                        val color = Color(android.graphics.Color.parseColor(hexCode))
                        onColorSelected(color)
                    } catch (e: Exception) {
                        // On invalid code, revert to the last valid color
                        hexCode = String.format("#%08X", selectedColor.toArgb())
                    }
                    focusManager.clearFocus()
                }
            )
        )
    }
}


@Composable
private fun ToggleRow(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = isChecked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}