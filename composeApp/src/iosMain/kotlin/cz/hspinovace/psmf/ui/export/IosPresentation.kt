package cz.hspinovace.psmf.ui.export

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene

/**
 * The view controller to present a system sheet from: the top of the
 * presentation stack in the foreground scene's key window.
 *
 * Without deprecated API: `UIApplication.keyWindow` and `.windows` have
 * been deprecated since iOS 13, so this goes through the connected scenes,
 * and `UIWindowScene.keyWindow` (iOS 15, this app's minimum).
 *
 * Null only if the app has no foreground window, and then there is nothing
 * to show a sheet on anyway; callers report that as a failure.
 */
internal fun topViewController(): UIViewController? {
    val scene =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
            ?: return null
    var top = scene.keyWindow?.rootViewController ?: return null
    while (true) {
        top = top.presentedViewController ?: return top
    }
}

/**
 * Bytes for Foundation, from [cz.hspinovace.psmf.export.bytes] and nowhere
 * else, so the attachment, the shared file and the saved file are the same
 * bytes Android writes: the CSV's byte-order mark and CRLF included.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
    }
