package com.example.testingcast

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.testingcast.ui.navigation.AppNavigation
import com.example.testingcast.ui.theme.TestingCastTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        setContent {
            // TVs are viewed in dim rooms: always use the dark scheme there.
            TestingCastTheme(darkTheme = isTv || isSystemInDarkTheme()) {
                AppNavigation()
            }
        }
    }
}
