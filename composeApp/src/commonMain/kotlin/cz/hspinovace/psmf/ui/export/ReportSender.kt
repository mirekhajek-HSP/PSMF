package cz.hspinovace.psmf.ui.export

import cz.hspinovace.psmf.export.ZouDocument

/**
 * What the report is delivered as: an email to PSMF with the three
 * renderings attached.
 *
 * Email because it is already an accepted channel (analysis section 2.4) —
 * no account, no server and nothing for PSMF to adopt before the pilot can
 * run.
 */
data class ReportDelivery(
    val to: String,
    val subject: String,
    /** The formatted text, so the report is readable without opening anything. */
    val body: String,
    val documents: List<ZouDocument>,
)

/**
 * What happened when the referee pressed send, as far as the platform can
 * tell. Each one is named on screen: a cancelled or failed send is never
 * silent, and nothing claims a report went out that did not.
 *
 * Android can only ever report [DraftOpened] or [NoMailApp]: a chooser
 * says nothing about what happened after it. iOS's mail composer reports
 * back, so it can also say [HandedToMail], [DraftSaved] and [Cancelled].
 */
sealed interface SendOutcome {
    /** A draft addressed to PSMF is open in a mail app. The referee sends it. */
    data object DraftOpened : SendOutcome

    /**
     * iOS, the system share sheet: the referee picked an app and finished
     * in it. Whether it went to PSMF depends on what they typed as the
     * recipient, which the share sheet cannot fill in.
     */
    data object SharedWithoutRecipient : SendOutcome

    /** iOS Mail accepted it for sending (it may sit in the Outbox). */
    data object HandedToMail : SendOutcome

    /** iOS Mail kept it as a draft. Not sent. */
    data object DraftSaved : SendOutcome

    /** The referee backed out. Nothing was sent. */
    data object Cancelled : SendOutcome

    /** Nothing on the device can take an email with attachments. */
    data object NoMailApp : SendOutcome

    /** Something went wrong on the way. Nothing was sent. */
    data object Failed : SendOutcome
}

/**
 * Hands the report to the platform's mail client.
 *
 * **Opens a draft; it does not send.** The referee presses send, which
 * keeps the last word with the person whose name is on the report — and
 * means the app never needs a mail account or a credential of any kind.
 */
interface ReportSender {
    suspend fun send(delivery: ReportDelivery): SendOutcome

    /**
     * Whether the next [send] can put the PSMF address in the To field.
     * Asked before send, so the screen can say so while there is still time
     * to act on it. Android's chooser always can; iOS can only when Apple
     * Mail has an account, and otherwise falls back to the share sheet.
     */
    fun prefillsRecipient(): Boolean = true
}

/**
 * For targets with no mail client to hand: the JVM test host.
 */
class UnavailableReportSender : ReportSender {
    override suspend fun send(delivery: ReportDelivery): SendOutcome = SendOutcome.NoMailApp
}
