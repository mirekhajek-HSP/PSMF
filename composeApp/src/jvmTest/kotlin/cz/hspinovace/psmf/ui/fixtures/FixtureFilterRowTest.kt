package cz.hspinovace.psmf.ui.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.SeasonId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.FilterOptions
import cz.hspinovace.psmf.usecase.FixtureFilter
import cz.hspinovace.psmf.usecase.FixtureListing
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Narrowing the fixture list.
 *
 * One bundled group of twelve teams fits on a screen; nine divisions is on
 * the order of nine hundred teams and several thousand fixtures. These
 * tests are about the control, not about the filtering — `FixtureFilterTest`
 * covers what the filter does to the data.
 */
@OptIn(ExperimentalTestApi::class)
class FixtureFilterRowTest {
    @Test
    fun bothFiltersStartOnEverything() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Row(listing()) }

                onNodeWithText("Liga: Vše").assertIsDisplayed()
                onNodeWithText("Tým: Vše").assertIsDisplayed()
                // Nothing to clear yet, so nothing offers to.
                onNodeWithText("Zrušit filtr").assertDoesNotExist()
            }
        }

    @Test
    fun pickingALeagueReportsIt() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent { Row(listing(), onFilterChanged = { chosen = it }) }

                onNodeWithText("Liga: Vše").performClick()
                onNodeWithText("7. liga A").performClick()
            }
            assertEquals(GroupId("7a"), chosen?.groupId)
            assertEquals(null, chosen?.teamId)
        }

    @Test
    fun theTeamMenuPutsFollowedTeamsUnderTheirOwnHeading() =
        runComposeUiTest {
            // The reason the Týmy tab has a follow button at all: at league
            // scale this menu is otherwise a nine-hundred-item scroll.
            withLanguage("cs") {
                setContent { Row(listing()) }

                onNodeWithText("Tým: Vše").performClick()

                onNodeWithText("Sledované").assertIsDisplayed()
                onNodeWithText("Ostatní").assertIsDisplayed()
                onNodeWithText("Sp. Sumýš").assertIsDisplayed()
                onNodeWithText("Kominíci").assertIsDisplayed()
            }
        }

    @Test
    fun pickingATeamReportsItAndKeepsTheLeagueAlone() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            val current = FixtureFilter(groupId = GroupId("6k"))
            withLanguage("cs") {
                setContent { Row(listing(current), onFilterChanged = { chosen = it }) }

                onNodeWithText("Tým: Vše").performClick()
                onNodeWithText("Sp. Sumýš").performClick()
            }
            assertEquals(TeamId("sp-sumys"), chosen?.teamId)
            assertEquals(GroupId("6k"), chosen?.groupId, "picking a team widened the league")
        }

    @Test
    fun aFilterInForceNamesItselfAndOffersToGo() =
        runComposeUiTest {
            // "Liga: 6. liga K" rather than "6. liga K", so a short list says
            // *why* it is short.
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent {
                    Row(
                        listing(FixtureFilter(groupId = GroupId("6k"), teamId = TeamId("kominici"))),
                        onFilterChanged = { chosen = it },
                    )
                }

                onNodeWithText("Liga: 6. liga K").assertIsDisplayed()
                onNodeWithText("Tým: Kominíci").assertIsDisplayed()

                onNodeWithText("Zrušit filtr").performClick()
            }
            assertEquals(FixtureFilter(), chosen)
        }

    @Test
    fun theMenusStayCompleteWhileAFilterIsInForce() =
        runComposeUiTest {
            // Options built from the filtered data would vanish as soon as
            // they were used, leaving no way back except knowing to clear the
            // filter first.
            withLanguage("cs") {
                setContent { Row(listing(FixtureFilter(groupId = GroupId("6k")))) }

                onNodeWithText("Liga: 6. liga K").performClick()

                onNodeWithText("7. liga A").assertIsDisplayed()
                onNodeWithText("Vše").assertIsDisplayed()
            }
        }

    // -----------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------

    @Composable
    private fun Row(
        listing: FixtureListing,
        onFilterChanged: (FixtureFilter) -> Unit = {},
    ) {
        PsmfTheme {
            FixtureFilterRow(listing = listing, onFilterChanged = onFilterChanged)
        }
    }

    private fun listing(filter: FixtureFilter = FixtureFilter()): FixtureListing =
        FixtureListing(
            groups = emptyList(),
            filter = filter,
            options =
                FilterOptions(
                    leagues = listOf(SIXTH, SEVENTH),
                    followedTeams = listOf(team("sp-sumys", "Sp. Sumýš")),
                    otherTeams = listOf(team("kominici", "Kominíci")),
                ),
        )

    private fun team(
        ref: String,
        name: String,
    ) = Team(
        id = TeamId(ref),
        ref = ref,
        groupId = SIXTH.id,
        name = name,
        kits = listOf(Kit(KitId("kit-$ref"), "modrá", listOf("modrá"))),
    )

    private companion object {
        val SIXTH =
            Group(
                id = GroupId("6k"),
                seasonId = SeasonId("2026-podzim"),
                name = "6. liga K",
                reportCode = "6K",
            )

        val SEVENTH =
            Group(
                id = GroupId("7a"),
                seasonId = SeasonId("2026-podzim"),
                name = "7. liga A",
                reportCode = "7A",
            )
    }
}
