package cz.hspinovace.psmf.ui.export

import cz.hspinovace.psmf.export.ZouDocument

/*
 * The decisions behind sending and saving, kept out of the platform glue.
 *
 * iOS code cannot run anywhere in this project's loop: there is no
 * simulator on the Intel Mac and iosArm64 tests have no executor. So what
 * can be a plain function is one, lives here, and is tested on the JVM. The
 * UIKit side only translates its callbacks into these inputs.
 */

/**
 * What the referee is told after iOS's mail composer closes.
 *
 * "Sent" from the composer means Mail *accepted* it; it may still be in the
 * Outbox, so it is [SendOutcome.HandedToMail], not a claim of delivery.
 */
fun mailComposerOutcome(result: MailComposerResult): SendOutcome =
    when (result) {
        MailComposerResult.SENT -> SendOutcome.HandedToMail
        MailComposerResult.SAVED -> SendOutcome.DraftSaved
        MailComposerResult.CANCELLED -> SendOutcome.Cancelled
        MailComposerResult.FAILED -> SendOutcome.Failed
    }

/**
 * What the referee is told after the system share sheet closes.
 *
 * An error wins over "completed". Completed means the referee finished in
 * *some* app, and the share sheet cannot fill in a recipient, so it is never
 * reported as reaching PSMF.
 */
fun shareSheetOutcome(
    completed: Boolean,
    failed: Boolean,
): SendOutcome =
    when {
        failed -> SendOutcome.Failed
        completed -> SendOutcome.SharedWithoutRecipient
        else -> SendOutcome.Cancelled
    }

/**
 * Saves [documents] into [folder], asking for one with [pickFolder] if there
 * is none, and says what happened.
 *
 * No folder, and the referee backs out of picking one: [SaveOutcome.Cancelled].
 * Stops at the first write that fails: a partial save looks complete from
 * the file list, so it must not quietly carry on. [F] is whatever the
 * platform calls a folder.
 */
suspend fun <F : Any> saveAll(
    folder: F?,
    pickFolder: suspend () -> F?,
    documents: List<ZouDocument>,
    write: (F, ZouDocument) -> Boolean,
): SaveOutcome {
    val target = folder ?: pickFolder() ?: return SaveOutcome.Cancelled
    return if (documents.all { write(target, it) }) SaveOutcome.Saved else SaveOutcome.Failed
}
