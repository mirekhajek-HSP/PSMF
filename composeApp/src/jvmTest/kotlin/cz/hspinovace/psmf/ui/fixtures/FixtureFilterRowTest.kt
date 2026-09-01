package cz.hspinovace.psmf.ui.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.SeasonId
import cz.hspinovace.psmf.domain.Venue
import cz.hspinovace.psmf.domain.VenueCode
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.FilterOptions
import cz.hspinovace.psmf.usecase.FixtureFilter
import cz.hspinovace.psmf.usecase.FixtureListing
import cz.hspinovace.psmf.usecase.LeagueLevelOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Narrowing the fixture list.
 *
 * One bundled group of twelve teams fits on a screen; nine divisions is on
 * the order of nine hundred teams and several thousand fixtures. These
 * tests are about the control, not about the filtering -- `FixtureFilterTest`
 * (in the shared module) covers what the filter does to the data.
 *
 * The test data below always offers two league levels (6 and 7), and level
 * 6 always holds two groups (K and L): a cascade with only one branch at
 * each step would not catch a level or a group picking the wrong sibling.
 */
@OptIn(ExperimentalTestApi::class)
class FixtureFilterRowTest {
    @Test
    fun everyLevelIsOfferedAndNoneIsPickedYet() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Row(listing()) }

                onNodeWithText("Liga").assertIsDisplayed()
                onNodeWithText("6").assertIsDisplayed()
                onNodeWithText("7").assertIsDisplayed()
                onAllNodesWithText("Vše")[0].assertIsSelected()
                // No level chosen, so there is no letter row to show yet.
                onNodeWithText("Skupina").assertDoesNotExist()
                // Nothing to clear yet, so nothing offers to.
                onNodeWithText("Zrušit filtr").assertDoesNotExist()
            }
        }

    @Test
    fun pickingALevelReportsItWithNoGroupYet() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent { Row(listing(), onFilterChanged = { chosen = it }) }

                onNodeWithText("7").performClick()
            }
            assertEquals(7, chosen?.leagueLevel)
            assertNull(chosen?.groupId)
        }

    @Test
    fun pickingALevelRevealsItsGroupLetters() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Row(listing(FixtureFilter(leagueLevel = 6))) }

                onNodeWithText("Skupina").assertIsDisplayed()
                onNodeWithText("K").assertIsDisplayed()
                onNodeWithText("L").assertIsDisplayed()
                // The other level's own letter is not offered here.
                onNodeWithText("A").assertDoesNotExist()
            }
        }

    @Test
    fun pickingAGroupLetterSetsBothTheLevelAndTheGroup() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent {
                    Row(listing(FixtureFilter(leagueLevel = 6)), onFilterChanged = { chosen = it })
                }

                onNodeWithText("L").performClick()
            }
            assertEquals(6, chosen?.leagueLevel)
            assertEquals(GroupId("6l"), chosen?.groupId)
        }

    @Test
    fun changingTheLevelClearsWhicheverGroupWasPicked() =
        runComposeUiTest {
            // "Changing the league clears the group" -- otherwise a stale
            // groupId from level 6 would silently narrow level 7 to
            // nothing, or worse, to the wrong group entirely.
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent {
                    Row(
                        listing(FixtureFilter(leagueLevel = 6, groupId = GroupId("6l"))),
                        onFilterChanged = { chosen = it },
                    )
                }

                onNodeWithText("7").performClick()
            }
            assertEquals(7, chosen?.leagueLevel)
            assertNull(chosen?.groupId, "changing the league did not clear the group")
        }

    @Test
    fun theGroupRowsOwnAnyChipWidensBackToTheWholeLevel() =
        runComposeUiTest {
            // A league alone is a valid filter: this is the fast way back
            // to it without losing the level already chosen.
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent {
                    Row(
                        listing(FixtureFilter(leagueLevel = 6, groupId = GroupId("6k"))),
                        onFilterChanged = { chosen = it },
                    )
                }

                // Index 1: the league row's own "Vše" is index 0.
                onAllNodesWithText("Vše")[1].performClick()
            }
            assertEquals(6, chosen?.leagueLevel, "the level widened too, not just the group")
            assertNull(chosen?.groupId)
        }

    @Test
    fun pickingAPitchReportsIt() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent { Row(listing(), onFilterChanged = { chosen = it }) }

                onNodeWithText("ZAKOS").performClick()
            }
            assertEquals(VenueCode("ZAKOS"), chosen?.venue)
        }

    @Test
    fun typingATeamNameReportsIt() =
        runComposeUiTest {
            // A text field, not a picker: nine hundred teams is not a list
            // a chip row survives.
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent { Row(listing(), onFilterChanged = { chosen = it }) }

                onNodeWithText("Tým").performTextInput("Kom")
            }
            assertEquals("Kom", chosen?.teamQuery)
        }

    @Test
    fun aFilterInForceOffersToClearAllOfIt() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            val current =
                FixtureFilter(
                    leagueLevel = 6,
                    groupId = GroupId("6k"),
                    venue = VenueCode("ZAKOS"),
                    teamQuery = "Kom",
                )
            withLanguage("cs") {
                setContent { Row(listing(current), onFilterChanged = { chosen = it }) }

                onNodeWithText("Zrušit filtr").performClick()
            }
            assertEquals(FixtureFilter(), chosen)
        }

    @Test
    fun theChipsStayCompleteWhileAFilterIsInForce() =
        runComposeUiTest {
            // Chips built from the filtered data would vanish as soon as
            // they were used, leaving no way back except knowing to clear
            // the filter first.
            withLanguage("cs") {
                setContent { Row(listing(FixtureFilter(leagueLevel = 6, groupId = GroupId("6k")))) }

                onNodeWithText("7").assertIsDisplayed()
                onNodeWithText("MIK").assertIsDisplayed()
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
                    leagueLevels =
                        listOf(
                            LeagueLevelOption(6, listOf(SIXTH_K, SIXTH_L)),
                            LeagueLevelOption(7, listOf(SEVENTH)),
                        ),
                    venues = listOf(Venue(VenueCode("ZAKOS")), Venue(VenueCode("MIK"))),
                ),
        )

    private companion object {
        val SIXTH_K =
            Group(id = GroupId("6k"), seasonId = SeasonId("2026-podzim"), name = "6. liga K", reportCode = "6K")

        val SIXTH_L =
            Group(id = GroupId("6l"), seasonId = SeasonId("2026-podzim"), name = "6. liga L", reportCode = "6L")

        val SEVENTH =
            Group(id = GroupId("7a"), seasonId = SeasonId("2026-podzim"), name = "7. liga A", reportCode = "7A")
    }
}
