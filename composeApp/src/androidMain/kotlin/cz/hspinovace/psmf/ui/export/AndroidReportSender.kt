package cz.hspinovace.psmf.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import cz.hspinovace.psmf.export.ZouDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes the three files and opens a mail draft with them attached.
 *
 * The attachments are the point: a formatted email is pleasant, but what
 * replaces a week of retyping is a spreadsheet their crew can open
 * (analysis section 1). The body carries the readable version so the
 * report is legible without opening anything.
 *
 * Files go to the app's own storage and are shared through a
 * `FileProvider`, so nothing is written anywhere the user has to clean up
 * and no storage permission is involved.
 *
 * Deliberately not the cache directory. Android empties caches whenever it
 * wants the space, and on a nearly full phone it does so within seconds of
 * the chooser opening — which silently strips the attachments off a report
 * the referee believes they have sent. Each send prunes what the previous
 * one left, so the directory holds one report at a time.
 */
class AndroidReportSender(
    private val context: Context,
) : ReportSender {
    override suspend fun send(delivery: ReportDelivery): Boolean =
        withContext(Dispatchers.IO) {
            val directory = reportsDirectory()
            val keep = delivery.documents.map { it.fileName }.toSet()
            directory.listFiles()?.forEach { if (it.name !in keep) it.delete() }
            val uris = ArrayList(delivery.documents.map { it.writeTo(directory) })

            val intent =
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    // A mail-ish MIME type so the chooser offers mail clients
                    // first; the attachments carry their own types.
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(delivery.to))
                    putExtra(Intent.EXTRA_SUBJECT, delivery.subject)
                    putExtra(Intent.EXTRA_TEXT, delivery.body)
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            val chooser =
                Intent.createChooser(intent, delivery.subject).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            // resolveActivity is deprecated for querying, but here it only
            // decides whether to tell the referee nothing can handle it
            // rather than throwing in their face at the pitch.
            runCatching { context.startActivity(chooser) }.isSuccess
        }

    private fun reportsDirectory(): File = File(context.filesDir, REPORTS).apply { mkdirs() }

    private fun ZouDocument.writeTo(directory: File): Uri {
        val file = File(directory, fileName)
        file.writeText(content)
        return FileProvider.getUriForFile(context, "${context.packageName}.$REPORTS", file)
    }

    private companion object {
        /** Matches the provider authority and the paths file in :androidApp. */
        const val REPORTS = "reports"
    }
}
