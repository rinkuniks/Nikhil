package com.example.testingcast.ui.common

import android.content.pm.PackageManager
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.core.layout.WindowSizeClass

@Composable
fun isTelevision(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
}

/** True for tablets, unfolded foldables, desktops and TVs (>= 840dp wide). */
@Composable
fun isExpandedWidth(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/** True for >= 600dp wide windows (small tablets, landscape phones on some devices). */
@Composable
fun isMediumWidth(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
