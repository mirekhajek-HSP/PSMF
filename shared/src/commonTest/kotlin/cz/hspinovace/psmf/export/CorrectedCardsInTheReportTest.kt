package cz.hspinovace.psmf.export

import cz.hspinovace.psmf.domain.CardEvent
import cz.hspinovace.psmf.domain.CardReason
import cz.hspinovace.psmf.domain.CardSubject
import cz.hspinovace.psmf.domain.CardsSection
import cz.hspinovace.psmf.domain.Fixtures
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.Minute
import cz.hspinovace.psmf.domain.RedCard
import cz.hspinovace.psmf.domain.YellowCard
import cz.hspinovace.psmf.usecase.CorrectCard
import cz.hspinovace.psmf.usecase.FakeLeagueRepository
import cz.hspinovace.psmf.usecase.FakeMatchRepository
import cz.hspinovace.psmf.usecase.RemoveCard
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A card corrected or removed in the console is what the report says, in all three
 * formats (0.4.0, Gate 6). No record of the change is kept: the report says what the
 * cards now are.
 */
class CorrectedCardsInTheReportTest {
    private val league = FakeLeagueRepository(listOf(CompleteReport.leagueGroup))

    /** The complete report's three cards, as the console has numbered them since 0.2.0. */
    private val numbered: Match =
        CompleteReport.match.copy(
            cards = CardsSection.Issued(CompleteReport.match.cardEvents.numberedFromOne()),
        )

    private fun List<CardEvent>.numberedFromOne() =
        mapIndexed { i, card ->
            when (card) {
                is YellowCard -> card.copy(sequence = i + 1)
                is RedCard -> card.copy(sequence = i + 1)
            }
        }

    @Test
    fun theTextCsvAndJsonSayWhatTheCardsNowAre() =
        runTest {
            val matches = FakeMatchRepository(listOf(numbered))
            val saved = Instant.parse("2026-08-31T20:30:00Z")
            // Bača's 20´ yellow was for holding, at 22´; Lepiš's yellow was not his.
            var match =
                CorrectCard(matches)(
                    numbered,
                    1,
                    CardSubject.Player(Fixtures.bacaAppearance.id),
                    CardReason("držení"),
                    Minute.Played(22),
                    saved,
                )
            match = RemoveCard(matches)(match, 2)

            val report = assertNotNull(BuildZouReport(league, NoAddedPlayers())(match))

            assertEquals(listOf("22´"), report.cards.yellow.map { it.minute })
            assertEquals(listOf("držení"), report.cards.yellow.map { it.reason })
            listOf(
                "text" to ZouText.format(report),
                "csv" to ZouCsv.format(report),
                "json" to ZouJson.format(report),
            ).forEach { (format, written) ->
                assertTrue("držení" in written, "$format does not have the corrected reason: $written")
                assertFalse("podražení" in written, "$format still has the old reason: $written")
                assertFalse("Lepi" in written, "$format still has the removed card: $written")
            }
        }
}
