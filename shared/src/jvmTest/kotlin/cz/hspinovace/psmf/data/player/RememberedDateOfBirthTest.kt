package cz.hspinovace.psmf.data.player

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.data.match.SqlDelightMatchRepository
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.export.CompleteReport
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A date of birth written at the pitch is remembered on the device, and
 * offered at the next match: a pre-fill with the standing of a corrected
 * jersey number. Against a real SQLite file, because "remembered" means
 * "still there after the process has gone".
 */
class RememberedDateOfBirthTest {
    private val file: File = File.createTempFile("psmf-dob-", ".db").also { it.delete() }

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    private suspend fun <T> session(block: suspend (PsmfDatabase) -> T): T {
        val driver = DatabaseDriverFactory("jdbc:sqlite:${file.absolutePath}").create()
        try {
            return block(PsmfDatabase(driver))
        } finally {
            driver.close()
        }
    }

    private val bilek = PlayerId("bilek-ondrej")

    @Test
    fun aDateWrittenAtOneMatchIsStillThereForTheNext() =
        runTest {
            session { SqlDelightRememberedDateOfBirthRepository(it).remember(bilek, LocalDate(1992, 5, 18)) }

            val remembered = session { SqlDelightRememberedDateOfBirthRepository(it).all() }

            assertEquals(mapOf(bilek to LocalDate(1992, 5, 18)), remembered)
        }

    @Test
    fun theLatestDateWrittenIsTheOneOffered() =
        runTest {
            session {
                val dates = SqlDelightRememberedDateOfBirthRepository(it)
                dates.remember(bilek, LocalDate(1992, 5, 18))
                dates.remember(bilek, LocalDate(1993, 5, 18))
            }

            assertEquals(LocalDate(1993, 5, 18), session { SqlDelightRememberedDateOfBirthRepository(it).all()[bilek] })
        }

    @Test
    fun rememberingADateDoesNotAlterAReportAlreadyStored() =
        runTest {
            // What a report wrote in Číslo RP is on the appearance, written on
            // the day. The remembered date is a different table.
            val report = CompleteReport.match
            val player = assertNotNull(report.homeLineup).appearances.first().playerId
            session { SqlDelightMatchRepository(it).save(report) }

            session { SqlDelightRememberedDateOfBirthRepository(it).remember(player, LocalDate(1970, 1, 1)) }

            assertEquals(report, session { SqlDelightMatchRepository(it).load(report.id) })
        }
}
