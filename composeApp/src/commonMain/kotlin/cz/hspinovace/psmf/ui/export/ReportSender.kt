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
 * Hands the report to the platform's mail client.
 *
 * **Opens a draft; it does not send.** The referee presses send, which
 * keeps the last word with the person whose name is on the report — and
 * means the app never needs a mail account or a credential of any kind.
 */
interface ReportSender {
    /** False when nothing on the device can handle it. */
    suspend fun send(delivery: ReportDelivery): Boolean
}

/**
 * For targets with no mail client to hand: the JVM test host, and iOS
 * until somebody builds it on a Mac.
 */
class UnavailableReportSender : ReportSender {
    override suspend fun send(delivery: ReportDelivery): Boolean = false
}
