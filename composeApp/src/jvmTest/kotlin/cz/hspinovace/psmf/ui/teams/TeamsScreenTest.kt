package cz.hspinovace.psmf.ui.teams

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.SeasonId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.LeagueTeams
import cz.hspinovace.psmf.usecase.TeamCard
import cz.hspinovace.psmf.usecase.TeamDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Týmy tab, driven through the screen rather than the use case.
 *
 * The search itself is tested in `BrowseTeamsTest`; what is tested here is
 * that typing reaches it, that following a team reports the right team and
 * the right direction, and that the followed section behaves the way the
 * screen claims.
 */
@OptIn(ExperimentalTestApi::class)
class TeamsScreenTest {
    @Test
    fun theTeamsOfEachLeagueAreListedUnderIt() =
        runComposeUiTest {
            // Nothing followed, so each team is drawn exactly once and the
            // assertions can be about the league section.
            withLanguage("cs") {
                setContent { Screen(directory(followed = false)) }

                onNodeWithText("6. liga K").assertIsDisplayed()
                onNodeWithText("Kominíci").assertIsDisplayed()
                onNodeWithText("Sp. Sumýš").assertIsDisplayed()
                // The squad size, because an empty squad is bad data the
                // referee should see before the match rather than at it.
                onAllNodesWithText("12 na soupisce").assertCountEquals(2)
            }
        }

    @Test
    fun aFollowedTeamIsDrawnBothAtTheTopAndUnderItsLeague() =
        runComposeUiTest {
            // Following is a shortcut, not a move. The team stays where a
            // referee browsing by league would expect to find it, which is
            // also why the list keys are prefixed by section.
            withLanguage("cs") {
                setContent { Screen(directory()) }

                onAllNodesWithText("Sp. Sumýš").assertCountEquals(2)
                onAllNodesWithText("Kominíci").assertCountEquals(1)
            }
        }

    @Test
    fun typingInTheSearchFieldReachesTheQuery() =
        runComposeUiTest {
            var typed: String? = null
            withLanguage("cs") {
                setContent {
                    Screen(directory(), onEvent = { if (it is TeamsEvent.QueryChanged) typed = it.query })
                }

                onNodeWithText("Hledat tým").performTextInput("sumys")
            }
            assertEquals("sumys", typed)
        }

    @Test
    fun followingATeamReportsThatTeamAndThatDirection() =
        runComposeUiTest {
            var event: TeamsEvent.FollowToggled? = null
            withLanguage("cs") {
                setContent {
                    Screen(
                        directory(),
                        onEvent = { if (it is TeamsEvent.FollowToggled) event = it },
                    )
                }

                // Two teams, one row each, and only one of them is offered
                // "Sledovat" -- the other is already followed.
                onNodeWithText("Sledovat").performClick()
            }
            assertEquals(TeamId("kominici"), event?.teamId)
            assertEquals(true, event?.followed)
        }

    @Test
    fun unfollowingIsOfferedForATeamAlreadyFollowed() =
        runComposeUiTest {
            var event: TeamsEvent.FollowToggled? = null
            withLanguage("cs") {
                setContent {
                    Screen(
                        directory(),
                        onEvent = { if (it is TeamsEvent.FollowToggled) event = it },
                    )
                }

                onNodeWithText("Sledované týmy").assertIsDisplayed()
                // Two of them, for the one team: the followed section and
                // the league section. Either is the same tap.
                onAllNodesWithText("Nesledovat")[0].performClick()
            }
            assertEquals(TeamId("sp-sumys"), event?.teamId)
            assertEquals(false, event?.followed)
        }

    @Test
    fun tappingATeamOpensIt() =
        runComposeUiTest {
            var opened: TeamId? = null
            withLanguage("cs") {
                setContent { Screen(directory(), onOpenTeam = { opened = it }) }

                onNodeWithText("Kominíci").performClick()
            }
            assertEquals(TeamId("kominici"), opened)
        }

    @Test
    fun followingNothingYetSaysWhatFollowingIsFor() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Screen(directory(followed = false)) }

                onNodeWithText("Sledované týmy").assertIsDisplayed()
                onNodeWithText("Sledováním", substring = true).assertIsDisplayed()
            }
        }

    @Test
    fun aSearchWithNoMatchesNamesWhatWasSearchedFor() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent {
                    Screen(TeamDirectory(query = "Slavia", followed = emptyList(), leagues = emptyList()))
                }

                onNodeWithText("Slavia", substring = true).assertIsDisplayed()
            }
        }

    @Test
    fun theFollowedSectionIsNotShownWhileSearching() =
        runComposeUiTest {
            // A search is a question about every team. An empty box captioned
            // "followed" is not an answer to it.
            withLanguage("cs") {
                setContent { Screen(directory().copy(query = "kom")) }

                onNodeWithText("Kominíci").assertIsDisplayed()
                onNodeWithText("Sledované týmy").assertDoesNotExist()
            }
        }

    @Test
    fun clearingTheSearchBringsTheFollowedSectionBackAtAll() =
        runComposeUiTest {
            // Weaker than it looks, and worth saying so. It would catch a
            // search that permanently dropped the followed list.
            //
            // It does **not** catch the defect the emulator found, which was
            // that the restored section is scrolled off the top of the
            // screen -- see the comment on the `LaunchedEffect` in
            // `TeamsScreen`. Verified by deleting that line and watching
            // this pass.
            withLanguage("cs") {
                setContent { Phone { Screen(directory()) } }

                // "kom" matches Kominíci, which is not followed -- so the
                // followed section is both filtered empty and hidden.
                onNodeWithText("Hledat tým").performTextInput("kom")
                onNodeWithText("Sledované týmy").assertDoesNotExist()

                onNodeWithText("Hledat tým").performTextClearance()

                onNodeWithText("Sledované týmy").assertIsDisplayed()
                onAllNodesWithText("Sp. Sumýš")[0].assertIsDisplayed()
            }
        }

    // -----------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------

    /**
     * A container the size of the phone the demo runs on.
     *
     * Without it the list is as tall as it likes, nothing scrolls, and a
     * whole class of defect is invisible.
     */
    @Composable
    private fun Phone(content: @Composable () -> Unit) {
        Box(modifier = Modifier.size(width = PHONE_WIDTH, height = PHONE_HEIGHT)) { content() }
    }

    @Composable
    private fun Screen(
        directory: TeamDirectory,
        onEvent: (TeamsEvent) -> Unit = {},
        onOpenTeam: (TeamId) -> Unit = {},
    ) {
        // Typing re-filters, the way it does in the app where the
        // ViewModel owns the query and re-runs the use case. A harness that
        // only recorded the keystroke would let a search test pass without
        // the list ever changing.
        var state by remember { mutableStateOf(TeamsUiState(loading = false, directory = directory)) }
        PsmfTheme {
            TeamsScreen(
                state = state,
                onEvent = { event ->
                    if (event is TeamsEvent.QueryChanged) {
                        state = state.copy(query = event.query, directory = directory.matching(event.query))
                    }
                    onEvent(event)
                },
                onOpenTeam = onOpenTeam,
            )
        }
    }

    /**
     * The filtering `BrowseTeams` does, in the crudest form that serves a
     * screen test. Diacritic folding is tested where it lives.
     */
    private fun TeamDirectory.matching(query: String): TeamDirectory {
        val needle = query.lowercase()

        fun List<TeamCard>.hits() =
            filter {
                needle.isEmpty() ||
                    it.team.name
                        .lowercase()
                        .contains(needle)
            }
        return TeamDirectory(
            query = query,
            followed = followed.hits(),
            leagues = leagues.map { LeagueTeams(it.group, it.teams.hits()) },
        )
    }

    private fun directory(followed: Boolean = true): TeamDirectory {
        val cards =
            listOf(
                card(TeamId("kominici"), "Kominíci", followed = false),
                card(TeamId("sp-sumys"), "Sp. Sumýš", followed = followed),
            )
        return TeamDirectory(
            query = "",
            followed = cards.filter { it.followed },
            leagues = listOf(LeagueTeams(GROUP, cards)),
        )
    }

    private fun card(
        id: TeamId,
        name: String,
        followed: Boolean,
    ) = TeamCard(
        team =
            Team(
                id = id,
                ref = id.value,
                groupId = GROUP.id,
                name = name,
                kits = listOf(Kit(KitId("kit-${id.value}"), "modrá", listOf("modrá"))),
            ),
        group = GROUP,
        followed = followed,
        squadSize = SQUAD_SIZE,
    )

    private companion object {
        val GROUP =
            Group(
                id = GroupId("6k"),
                seasonId = SeasonId("2026-podzim"),
                name = "6. liga K",
                reportCode = "6K",
            )

        /** What the bundled data actually holds per team. */
        const val SQUAD_SIZE = 12

        /** A Medium Phone, which is what the emulator and the demo use. */
        val PHONE_WIDTH = 411.dp
        val PHONE_HEIGHT = 866.dp
    }
}
