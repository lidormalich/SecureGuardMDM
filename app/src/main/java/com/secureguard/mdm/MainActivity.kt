package com.secureguard.mdm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.secureguard.mdm.ui.navigation.AppNavigation
import com.secureguard.mdm.ui.theme.SecureGuardTheme
import com.secureguard.mdm.utils.SecureUpdateHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var secureUpdateHelper: SecureUpdateHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!secureUpdateHelper.coreComponentExists()) {
            throw RuntimeException("Core validation component is missing or corrupted. Halting execution.")
        }

        setContent {
            SecureGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}