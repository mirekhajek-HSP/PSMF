package cz.hspinovace.psmf.ui.export

/** What happened when the referee pressed *Zkopírovat text zápisu*. Each is named on screen. */
sealed interface CopyOutcome {
    /** The text is on the clipboard. */
    data object Copied : CopyOutcome

    /** The platform refused, or has no clipboard to hand. Nothing was copied. */
    data object Failed : CopyOutcome
}

/**
 * Puts the report's text on the clipboard, for the referee to paste into a mail or a chat.
 *
 * The platform's own, like [ReportSender]: Compose's clipboard cannot make a plain-text
 * entry from common code in this version, and Android 13 and later can be told the text is
 * sensitive (it holds dates of birth and card numbers), which only Android code can say.
 */
interface ReportClipboard {
    /** True when [text] is on the clipboard. */
    fun copy(text: String): Boolean
}

/** For targets with no clipboard to hand: the JVM test host. */
class UnavailableReportClipboard : ReportClipboard {
    override fun copy(text: String): Boolean = false
}
