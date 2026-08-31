package cz.hspinovace.psmf.data.team

import cz.hspinovace.psmf.data.db.DatabaseDriverFactory
import cz.hspinovace.psmf.data.match.SqlDelightMatchRepository
import cz.hspinovace.psmf.db.PsmfDatabase
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.export.CompleteReport
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Nothing already written moves.**
 *
 * A referee may correct a default jersey number on the Týmy tab. A report
 * they filed last week must read exactly as it did — the number on it was
 * true on the day, and a report that silently rewrites itself is worse than
 * one that is wrong, because nobody knows to check it.
 *
 * The property is structural: `appearance_record.jersey_number` is written
 * when the lineup is filled in, and the override table is a different table
 * that nothing in the report path reads. That is an argument, and this is
 * the test — the same way the kit-label snapshot is proved rather than
 * asserted.
 *
 * It runs against a real SQLite file rather than fakes, because the claim
 * is about two tables and a fake has neither.
 */
class JerseyOverrideTest {
    private val temporaryFiles = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        temporaryFiles.forEach { it.delete() }
    }

    private fun database(): PsmfDatabase {
        val file = File.createTempFile("psmf-jersey-", ".db").also { it.delete() }
        temporaryFiles += file
        return PsmfDatabase(DatabaseDriverFactory("jdbc:sqlite:${file.absolutePath}").create())
    }

    @Test
    fun changingADefaultDoesNotAlterAReportAlreadyStored() =
        runTest {
            val database = database()
            val matches = SqlDelightMatchRepository(database)
            val overrides = SqlDelightJerseyOverrideRepository(database)

            val report = CompleteReport.match
            matches.save(report)

            val appearance =
                assertNotNull(
                    report.homeLineup?.appearances?.firstOrNull { it.jerseyNumber != null },
                    "the fixture report has no numbered appearance to test against",
                )
            val wasWorn = assertNotNull(appearance.jerseyNumber)

            // The referee corrects the player's standing number to something
            // that is definitely not what they wore.
            val corrected = JerseyNumber(if (wasWorn.value == LOUD_NUMBER) LOUD_NUMBER - 1 else LOUD_NUMBER)
            overrides.setDefaultJerseyNumber(appearance.playerId, corrected)

            val reloaded = assertNotNull(matches.load(report.id))

            assertEquals(report, reloaded, "the stored report changed")
            val sameRow =
                reloaded.homeLineup
                    ?.appearances
                    .orEmpty()
                    .firstOrNull { it.id == appearance.id }
            assertEquals(
                wasWorn,
                sameRow?.jerseyNumber,
                "the number on the report followed the correction",
            )
            // And the correction is genuinely there, so the test above is
            // not passing because nothing was written.
            assertEquals(corrected, overrides.overrides()[appearance.playerId])
        }

    @Test
    fun aCorrectionIsRememberedAndClearingItRemovesTheRow() =
        runTest {
            val overrides = SqlDelightJerseyOverrideRepository(database())
            val lineup = assertNotNull(CompleteReport.match.homeLineup)
            val player = lineup.appearances.first().playerId

            overrides.setDefaultJerseyNumber(player, JerseyNumber(LOUD_NUMBER))
            assertEquals(JerseyNumber(LOUD_NUMBER), overrides.overrides()[player])

            // Written twice, because the referee corrects a correction.
            overrides.setDefaultJerseyNumber(player, JerseyNumber(1))
            assertEquals(JerseyNumber(1), overrides.overrides()[player])

            // Cleared means absent, not null: absence is what "the league's
            // number stands" is spelled as.
            overrides.setDefaultJerseyNumber(player, null)
            assertNull(overrides.overrides()[player])
            assertTrue(overrides.overrides().isEmpty())
        }

    @Test
    fun theFollowedListSurvivesTheProcessBeingKilled() =
        runTest {
            // Written through one connection and read through another, which
            // is what a process death is from the database's point of view.
            val file = File.createTempFile("psmf-followed-", ".db").also { it.delete() }
            temporaryFiles += file

            val team = TeamId("d58671d2-21b0-4d25-8728-8b280323f020")
            val first = DatabaseDriverFactory("jdbc:sqlite:${file.absolutePath}").create()
            try {
                SqlDelightFollowedTeamRepository(PsmfDatabase(first)).setFollowed(team, followed = true)
            } finally {
                first.close()
            }

            val second = DatabaseDriverFactory("jdbc:sqlite:${file.absolutePath}").create()
            try {
                assertEquals(
                    setOf(team),
                    SqlDelightFollowedTeamRepository(PsmfDatabase(second)).followed(),
                )
            } finally {
                second.close()
            }
        }

    @Test
    fun unfollowingRemovesTheTeamAndFollowingTwiceIsNotAnError() =
        runTest {
            val followed = SqlDelightFollowedTeamRepository(database())
            val team = TeamId("d58671d2-21b0-4d25-8728-8b280323f020")

            followed.setFollowed(team, followed = true)
            // A double tap, or a follow from the roster after one from the
            // list. `INSERT OR REPLACE`, so it restamps rather than throws.
            followed.setFollowed(team, followed = true)
            assertEquals(setOf(team), followed.followed())

            followed.setFollowed(team, followed = false)
            assertTrue(followed.followed().isEmpty())
            // Unfollowing something that was never followed is also not an
            // error: the screen offers the toggle, not a state machine.
            followed.setFollowed(team, followed = false)
            assertTrue(followed.followed().isEmpty())
        }

    private companion object {
        /** A number no fixture uses, so a mix-up cannot pass by accident. */
        const val LOUD_NUMBER = 99
    }
}
