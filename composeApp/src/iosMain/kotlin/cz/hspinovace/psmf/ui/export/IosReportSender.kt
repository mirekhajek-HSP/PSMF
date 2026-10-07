package cz.hspinovace.psmf.ui.export

import cz.hspinovace.psmf.export.ZouDocument
import cz.hspinovace.psmf.export.bytes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.writeToURL
import platform.MessageUI.MFMailComposeResult
import platform.MessageUI.MFMailComposeViewController
import platform.MessageUI.MFMailComposeViewControllerDelegateProtocol
import platform.UIKit.UIActivityItemSourceProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Sends the report on iOS: a draft to PSMF with the three files attached,
 * which the referee sends themselves. The app sends nothing, needs no
 * account and holds no credential, exactly as on Android.
 *
 * # Two routes, chosen at the moment of sending
 *
 * - **Apple Mail has an account** (`canSendMail`): the mail composer, with
 *   the recipient, subject, body and all three attachments filled in. The
 *   referee presses send. The composer reports back, so the screen can say
 *   handed over, kept as a draft, cancelled or failed.
 * - **It does not.** Many referees use Gmail or another app and never set
 *   up Apple Mail. The system share sheet then offers every app that takes
 *   files, with the subject, body and attachments, **but no share-sheet API
 *   can fill in a recipient.** So the PSMF address is copied to the
 *   clipboard first, and the export screen has already said so, before send
 *   was pressed ([prefillsRecipient]). Afterwards the screen says the report
 *   was handed over and asks the referee to check where it went; it does not
 *   claim it reached PSMF.
 *
 * The decisions are in `ExportRouting.kt` and tested on the JVM; this file
 * only presents the sheets and translates their callbacks.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosReportSender : ReportSender {
    /**
     * UIKit holds delegates weakly. Kotlin/Native would otherwise be free to
     * collect the delegate while its sheet is still on screen, and the
     * callback would never arrive.
     */
    private var inFlight: Any? = null

    override fun prefillsRecipient(): Boolean = MFMailComposeViewController.canSendMail()

    override suspend fun send(delivery: ReportDelivery): SendOutcome =
        withContext(Dispatchers.Main) {
            val presenter = topViewController() ?: return@withContext SendOutcome.Failed
            if (MFMailComposeViewController.canSendMail()) {
                compose(presenter, delivery)
            } else {
                share(presenter, delivery)
            }
        }

    private suspend fun compose(
        presenter: UIViewController,
        delivery: ReportDelivery,
    ): SendOutcome =
        suspendCancellableCoroutine { continuation ->
            val composer = MFMailComposeViewController()
            val delegate =
                MailDelegate { result ->
                    composer.dismissViewControllerAnimated(true, completion = null)
                    inFlight = null
                    if (continuation.isActive) continuation.resume(mailComposerOutcome(result))
                }
            inFlight = delegate
            composer.mailComposeDelegate = delegate
            composer.setToRecipients(listOf(delivery.to))
            composer.setSubject(delivery.subject)
            composer.setMessageBody(delivery.body, isHTML = false)
            delivery.documents.forEach { document ->
                composer.addAttachmentData(document.bytes().toNSData(), document.mimeType, document.fileName)
            }
            presenter.presentViewController(composer, animated = true, completion = null)
        }

    private suspend fun share(
        presenter: UIViewController,
        delivery: ReportDelivery,
    ): SendOutcome {
        val files = writeForSharing(delivery.documents) ?: return SendOutcome.Failed
        // The share sheet cannot address the mail. The screen has told the
        // referee to paste it; this is what makes that one tap.
        UIPasteboard.generalPasteboard.string = delivery.to

        return suspendCancellableCoroutine { continuation ->
            val text = SubjectAndBody(delivery.subject, delivery.body)
            val sheet = UIActivityViewController(activityItems = listOf(text) + files, applicationActivities = null)
            inFlight = text
            sheet.completionWithItemsHandler = { _, completed, _, error ->
                inFlight = null
                if (continuation.isActive) {
                    continuation.resume(shareSheetOutcome(completed = completed, failed = error != null))
                }
            }
            // On iPad the share sheet is a popover and UIKit throws if it
            // has nothing to point at. Anchored at the bottom centre, where
            // the send button is, with no arrow.
            sheet.popoverPresentationController?.let { popover ->
                val view = presenter.view
                popover.sourceView = view
                view.bounds.useContents {
                    popover.sourceRect = CGRectMake(size.width / 2, size.height, 0.0, 0.0)
                }
                popover.permittedArrowDirections = 0u
            }
            presenter.presentViewController(sheet, animated = true, completion = null)
        }
    }

    /**
     * Writes the attachments where the share sheet can read them: a
     * `reports` folder in the app's temporary directory, emptied first so it
     * holds one report at a time. Null if any file cannot be written; a
     * share with a missing attachment would look complete to the referee.
     */
    private fun writeForSharing(documents: List<ZouDocument>): List<NSURL>? {
        val manager = NSFileManager.defaultManager
        val folder = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent(REPORTS) ?: return null
        manager.removeItemAtURL(folder, error = null)
        if (!manager.createDirectoryAtURL(folder, true, null, null)) return null
        val files =
            documents.mapNotNull { document ->
                folder
                    .URLByAppendingPathComponent(document.fileName)
                    ?.takeIf { document.bytes().toNSData().writeToURL(it, atomically = true) }
            }
        return files.takeIf { it.size == documents.size }
    }

    private class MailDelegate(
        private val onFinish: (MailComposerResult) -> Unit,
    ) : NSObject(),
        MFMailComposeViewControllerDelegateProtocol {
        override fun mailComposeController(
            controller: MFMailComposeViewController,
            didFinishWithResult: MFMailComposeResult,
            error: NSError?,
        ) {
            onFinish(
                when (didFinishWithResult) {
                    MFMailComposeResult.MFMailComposeResultSent -> MailComposerResult.SENT
                    MFMailComposeResult.MFMailComposeResultSaved -> MailComposerResult.SAVED
                    MFMailComposeResult.MFMailComposeResultCancelled -> MailComposerResult.CANCELLED
                    else -> MailComposerResult.FAILED
                },
            )
        }
    }

    /**
     * The mail body, plus the subject for apps that ask for one. The share
     * sheet takes the subject from here; no API gives it a recipient.
     */
    private class SubjectAndBody(
        private val subject: String,
        private val body: String,
    ) : NSObject(),
        UIActivityItemSourceProtocol {
        override fun activityViewControllerPlaceholderItem(activityViewController: UIActivityViewController): Any = body

        @ObjCSignatureOverride
        override fun activityViewController(
            activityViewController: UIActivityViewController,
            itemForActivityType: String?,
        ): Any = body

        @ObjCSignatureOverride
        override fun activityViewController(
            activityViewController: UIActivityViewController,
            subjectForActivityType: String?,
        ): String = subject
    }

    private companion object {
        const val REPORTS = "reports"
    }
}
