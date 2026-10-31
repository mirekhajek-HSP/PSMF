package cz.hspinovace.psmf.ui.export

import platform.UIKit.UIPasteboard

/** The report's text on the general pasteboard. iOS has no way to mark it sensitive. */
class IosReportClipboard : ReportClipboard {
    override fun copy(text: String): Boolean {
        UIPasteboard.generalPasteboard.string = text
        return true
    }
}
