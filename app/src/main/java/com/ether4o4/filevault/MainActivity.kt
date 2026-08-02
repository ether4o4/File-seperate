package com.ether4o4.filevault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ether4o4.filevault.ui.explorer.ExplorerScreen
import com.ether4o4.filevault.ui.theme.FileVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FileVaultTheme {
                Surface(Modifier.fillMaxSize()) {
                    ExplorerScreen()
                }
            }
        }
    }
}
