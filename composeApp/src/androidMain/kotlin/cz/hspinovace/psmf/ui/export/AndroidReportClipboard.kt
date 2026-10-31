package cz.hspinovace.psmf.ui.export

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * The report's text on the system clipboard.
 *
 * From Android 13 the clip is marked sensitive: the keyboard's clipboard strip and the
 * system's copy preview show it hidden, because the text holds dates of birth and card
 * numbers. Earlier versions have no such mark and show the text.
 */
class AndroidReportClipboard(
    private val context: Context,
) : ReportClipboard {
    override fun copy(text: String): Boolean {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = ClipData.newPlainText(LABEL, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras =
                PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        return runCatching { manager.setPrimaryClip(clip) }.isSuccess
    }

    private companion object {
        const val LABEL = "ZoU"
    }
}
