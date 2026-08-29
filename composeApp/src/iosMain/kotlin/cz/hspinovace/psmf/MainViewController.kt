package cz.hspinovace.psmf

import androidx.compose.ui.window.ComposeUIViewController
import cz.hspinovace.psmf.di.initKoin
import platform.UIKit.UIViewController

/**
 * Entry point used by iosApp/. Built on macOS only.
 */
fun mainViewController(): UIViewController =
    ComposeUIViewController(
        configure = { initKoin() },
    ) {
        App()
    }
