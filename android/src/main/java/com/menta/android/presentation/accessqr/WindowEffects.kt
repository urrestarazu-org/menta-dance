package com.menta.android.presentation.accessqr

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Blocks screenshots and the Recents thumbnail while composed; clears the flag only if it set it. */
@Composable
fun SecureScreenEffect() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val wasSecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!wasSecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

/**
 * Raises the window brightness to maximum and keeps the screen on while composed, then restores both.
 * Window-level, so it needs no permission and never touches the system setting.
 */
@Composable
fun BrightnessBoostEffect() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val previousBrightness = window.attributes.screenBrightness
        val wasKeepingScreenOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
            if (!wasKeepingScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
