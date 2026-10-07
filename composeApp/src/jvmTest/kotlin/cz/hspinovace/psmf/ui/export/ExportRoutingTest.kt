package cz.hspinovace.psmf.ui.export

import cz.hspinovace.psmf.export.ZouDocument
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The logic under the iOS send and save glue, tested here because nothing
 * iOS-specific can run in this project's loop.
 */
class ExportRoutingTest {
    // ------------------------------------------------------------------
    // Mail composer
    // ------------------------------------------------------------------

    @Test
    fun mailSayingSentIsReportedAsHandedOverNotDelivered() {
        assertEquals(SendOutcome.HandedToMail, mailComposerOutcome(MailComposerResult.SENT))
    }

    @Test
    fun aDraftKeptInMailIsNotReportedAsSent() {
        assertEquals(SendOutcome.DraftSaved, mailComposerOutcome(MailComposerResult.SAVED))
    }

    @Test
    fun cancellingTheComposerIsCancelled() {
        assertEquals(SendOutcome.Cancelled, mailComposerOutcome(MailComposerResult.CANCELLED))
    }

    @Test
    fun aComposerFailureIsAFailure() {
        assertEquals(SendOutcome.Failed, mailComposerOutcome(MailComposerResult.FAILED))
    }

    // ------------------------------------------------------------------
    // Share sheet
    // ------------------------------------------------------------------

    @Test
    fun finishingInTheShareSheetIsNeverReportedAsReachingPsmf() {
        assertEquals(SendOutcome.SharedWithoutRecipient, shareSheetOutcome(completed = true, failed = false))
    }

    @Test
    fun dismissingTheShareSheetIsCancelled() {
        assertEquals(SendOutcome.Cancelled, shareSheetOutcome(completed = false, failed = false))
    }

    @Test
    fun anErrorWinsOverCompleted() {
        assertEquals(SendOutcome.Failed, shareSheetOutcome(completed = true, failed = true))
        assertEquals(SendOutcome.Failed, shareSheetOutcome(completed = false, failed = true))
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    private val documents =
        listOf("a.txt", "a.csv", "a.json").map { ZouDocument(it, "text/plain", it) }

    @Test
    fun aStoredFolderIsUsedWithoutAsking() =
        runTest {
            var asked = false
            val written = mutableListOf<String>()
            val outcome =
                saveAll(
                    folder = "stored",
                    pickFolder = {
                        asked = true
                        "picked"
                    },
                    documents = documents,
                ) { folder, document ->
                    written += "$folder/${document.fileName}"
                    true
                }

            assertEquals(SaveOutcome.Saved, outcome)
            assertEquals(false, asked)
            assertEquals(listOf("stored/a.txt", "stored/a.csv", "stored/a.json"), written)
        }

    @Test
    fun withNoFolderTheRefereeIsAskedAndThePickIsUsed() =
        runTest {
            val written = mutableListOf<String>()
            val outcome =
                saveAll<String>(folder = null, pickFolder = { "picked" }, documents = documents) { folder, document ->
                    written += "$folder/${document.fileName}"
                    true
                }

            assertEquals(SaveOutcome.Saved, outcome)
            assertEquals(3, written.size)
            assertEquals(true, written.all { it.startsWith("picked/") })
        }

    @Test
    fun backingOutOfThePickerIsCancelledAndWritesNothing() =
        runTest {
            var writes = 0
            val outcome =
                saveAll<String>(folder = null, pickFolder = { null }, documents = documents) { _, _ ->
                    writes++
                    true
                }

            assertEquals(SaveOutcome.Cancelled, outcome)
            assertEquals(0, writes)
        }

    @Test
    fun theFirstFailedWriteStopsTheSave() =
        runTest {
            val attempted = mutableListOf<String>()
            val outcome =
                saveAll(folder = "stored", pickFolder = { "picked" }, documents = documents) { _, document ->
                    attempted += document.fileName
                    document.fileName != "a.csv"
                }

            assertEquals(SaveOutcome.Failed, outcome)
            // The JSON after the failed CSV is never attempted.
            assertEquals(listOf("a.txt", "a.csv"), attempted)
        }
}
