package cz.hspinovace.psmf.ui.teams

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.domain.DisciplinaryRecord
import cz.hspinovace.psmf.domain.Group
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.Kit
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.RpNumber
import cz.hspinovace.psmf.domain.SeasonId
import cz.hspinovace.psmf.domain.Team
import cz.hspinovace.psmf.domain.TeamId
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.RosterRow
import cz.hspinovace.psmf.usecase.TeamRoster
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The roster screen: **one editable field, and a list of things that are
 * deliberately not editable.**
 */
@OptIn(ExperimentalTestApi::class)
class TeamRosterScreenTest {
    @Test
    fun theTeamItsLeagueAndBothKitSetsAreShown() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Screen() }

                onNodeWithText("Kominíci").assertIsDisplayed()
                onNodeWithText("6. liga K").assertIsDisplayed()
                onNodeWithText("modrá", substring = true).assertIsDisplayed()
                onNodeWithText("bílo-modrá", substring = true).assertIsDisplayed()
            }
        }

    @Test
    fun typingIntoAJerseyFieldReportsTheWholeFieldAndTheRightPlayer() =
        runComposeUiTest {
            var event: TeamRosterEvent.JerseyNumberChanged? = null
            withLanguage("cs") {
                setContent {
                    Screen(onEvent = { if (it is TeamRosterEvent.JerseyNumberChanged) event = it })
                }

                // Novák wears 9 and sorts first.
                onNodeWithText("9").performScrollTo().performTextInput("7")
            }
            assertEquals(PlayerId("novak"), event?.playerId)
            // 79, not 97: the caret in a freshly composed field sits at the
            // START. Worth having written down -- it is the reason the
            // ViewModel is handed the whole field rather than a keystroke,
            // and the reason it parses rather than appends.
            assertEquals("79", event?.text)
        }

    @Test
    fun clearingAFieldIsReportedAndMarkedRatherThanIgnored() =
        runComposeUiTest {
            // The referee halfway through replacing 9 with 7 has an empty
            // field. Dropping the keystroke would leave the old number on
            // screen with no sign that nothing happened.
            var event: TeamRosterEvent.JerseyNumberChanged? = null
            withLanguage("cs") {
                setContent {
                    Screen(onEvent = { if (it is TeamRosterEvent.JerseyNumberChanged) event = it })
                }

                onNodeWithText("9").performScrollTo().performTextClearance()

                onNodeWithText("0–99").assertIsDisplayed()
            }
            assertEquals("", event?.text)
        }

    @Test
    fun aCorrectedNumberOffersToGoBackToTheLeaguesOne() =
        runComposeUiTest {
            var cleared: PlayerId? = null
            withLanguage("cs") {
                setContent {
                    Screen(
                        state =
                            TeamRosterUiState(
                                loading = false,
                                roster = roster(correctedNumberFor = PlayerId("novak")),
                            ),
                        onEvent = { if (it is TeamRosterEvent.CorrectionCleared) cleared = it.playerId },
                    )
                }

                onNodeWithText("Vrátit číslo z ligy").performScrollTo().performClick()
            }
            assertEquals(PlayerId("novak"), cleared)
        }

    @Test
    fun aNumberThatWasNotCorrectedOffersNothingToRestore() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Screen() }

                onNodeWithText("Vrátit číslo z ligy").assertDoesNotExist()
            }
        }

    @Test
    fun theScreenSaysWhatItWillNotLetTheRefereeChange() =
        runComposeUiTest {
            // The rule is worth stating on the screen: a referee who came
            // here to fix a misspelled name should find out why they cannot,
            // rather than concluding the app is broken.
            withLanguage("cs") {
                setContent { Screen() }

                onNodeWithText("Upravit lze jen číslo dresu.", substring = true).assertIsDisplayed()
            }
        }

    @Test
    fun absenceIsNotHereAndTheScreenSaysWhereItIs() =
        runComposeUiTest {
            // The point most likely to be got wrong. Absence is a fact about
            // one match, not about a player.
            withLanguage("cs") {
                setContent { Screen() }

                onNodeWithText("Neúčast", substring = true).performScrollTo().assertIsDisplayed()
                // And there is no control for it anywhere on the screen.
                onNodeWithText("Nepřítomen").assertDoesNotExist()
            }
        }

    @Test
    fun cardHistoryIsShownAsInformationAndNeverAsClearance() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Screen() }

                onNodeWithText("ŽK v sezoně: 2").performScrollTo().assertIsDisplayed()
                // The app must never state that a player is eligible.
                onNodeWithText("Může hrát").assertDoesNotExist()
            }
        }

    @Test
    fun theRpColumnIsExplainedRatherThanLeftBlank() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Screen() }

                onNodeWithText("PSMF čísla RP zatím nedodala.", substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }

    @Test
    fun anRpNumberIsShownWhereTheLeagueHasIssuedOne() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Screen(state = TeamRosterUiState(loading = false, roster = roster(withRpNumber = true))) }

                onNodeWithText("Číslo RP: 980619").performScrollTo().assertIsDisplayed()
                // And the explanation for the missing ones is then gone.
                onNodeWithText("PSMF čísla RP zatím nedodala.", substring = true).assertDoesNotExist()
            }
        }

    @Test
    fun followingFromTheRosterReportsTheDirection() =
        runComposeUiTest {
            var followed: Boolean? = null
            withLanguage("cs") {
                setContent {
                    Screen(onEvent = { if (it is TeamRosterEvent.FollowToggled) followed = it.followed })
                }

                onNodeWithText("Sledovat").performClick()
            }
            assertEquals(true, followed)
        }

    @Test
    fun theFieldTextPrefersWhatIsBeingTypedOverWhatIsSaved() {
        // Not a UI test: the rule itself, which is what makes the field
        // behave. Kept beside the screen because it is about the screen.
        val state = TeamRosterUiState(unsaved = mapOf(PlayerId("novak") to "1"))

        assertEquals("1", state.fieldText(PlayerId("novak"), JerseyNumber(9)))
        assertTrue(state.isInError(PlayerId("novak")))

        assertEquals("9", state.fieldText(PlayerId("poupe"), JerseyNumber(9)))
        assertEquals("", state.fieldText(PlayerId("poupe"), null))
    }

    // -----------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------

    @Composable
    private fun Screen(
        state: TeamRosterUiState = TeamRosterUiState(loading = false, roster = roster()),
        onEvent: (TeamRosterEvent) -> Unit = {},
    ) {
        // Mirrored into local state so that clearing a field renders empty,
        // the way it does in the app, where the ViewModel keeps the text.
        var current by remember { mutableStateOf(state) }
        PsmfTheme {
            TeamRosterScreen(
                state = current,
                onEvent = { event ->
                    if (event is TeamRosterEvent.JerseyNumberChanged) {
                        current = current.copy(unsaved = current.unsaved + (event.playerId to event.text))
                    }
                    onEvent(event)
                },
            )
        }
    }

    private fun roster(
        correctedNumberFor: PlayerId? = null,
        withRpNumber: Boolean = false,
    ): TeamRoster {
        val team =
            Team(
                id = TeamId("kominici"),
                ref = "kominici",
                groupId = GROUP.id,
                name = "Kominíci",
                kits =
                    listOf(
                        Kit(KitId("kit-1"), "modrá", listOf("modrá")),
                        Kit(KitId("kit-2"), "bílo-modrá", listOf("bílá", "modrá")),
                    ),
            )
        val rows =
            listOf(
                row("novak", "Novák", "Jan", 9, correctedNumberFor, withRpNumber),
                row("poupe", "Poupě", "Petr", 11, correctedNumberFor, rp = false),
            )
        return TeamRoster(group = GROUP, team = team, followed = false, rows = rows)
    }

    private fun row(
        ref: String,
        surname: String,
        first: String,
        number: Int,
        correctedNumberFor: PlayerId?,
        rp: Boolean,
    ): RosterRow {
        val id = PlayerId(ref)
        return RosterRow(
            player =
                Player(
                    id = id,
                    ref = ref,
                    teamId = TeamId("kominici"),
                    name = PlayerName(PersonName.of(surname), PersonName.of(first)),
                    rpNumber = if (rp) RpNumber("980619") else null,
                    dateOfBirth = LocalDate(1990, 6, 15),
                    birthNumber = null,
                    defaultJerseyNumber = JerseyNumber(number),
                    // Only the first player carries cards. Two rows with the
                    // same count made the assertion match two nodes, which
                    // is a test that cannot say which row it read.
                    discipline =
                        if (ref == "novak") {
                            DisciplinaryRecord(YELLOWS, LocalDate(2026, 8, 24))
                        } else {
                            null
                        },
                ),
            jerseyNumber = JerseyNumber(number),
            corrected = id == correctedNumberFor,
        )
    }

    private companion object {
        val GROUP =
            Group(
                id = GroupId("6k"),
                seasonId = SeasonId("2026-podzim"),
                name = "6. liga K",
                reportCode = "6K",
            )

        const val YELLOWS = 2
    }
}
