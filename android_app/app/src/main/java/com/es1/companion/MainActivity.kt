package com.es1.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.es1.companion.ui.MainNavigation
import com.es1.companion.ui.theme.ES1CompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ES1CompanionTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainNavigation()
                }
            }
        }
    }
}
