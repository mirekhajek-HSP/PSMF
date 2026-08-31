package cz.hspinovace.psmf.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cz.hspinovace.psmf.data.settings.AppLanguage
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.MatchStatus
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.Score
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.console.ConsoleScreen
import cz.hspinovace.psmf.ui.console.ConsoleUiState
import cz.hspinovace.psmf.ui.locale.AppEnvironment
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.usecase.ConsoleEntry
import cz.hspinovace.psmf.usecase.ConsoleRow
import cz.hspinovace.psmf.usecase.ConsoleTeam
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val KICKOFF = Instant.parse("2026-08-31T19:00:00Z")

/**
 * **The phone is read by more than one person.**
 *
 * The captain confirms the lineup on the referee's phone and both captains
 * confirm the recap, so the language cannot be the device's — a Ukrainian
 * captain confirming on a Czech referee's phone is precisely the case three
 * languages exist for. The referee hands the phone over, changes the
 * language, and hands it back, **mid-match, without the app restarting.**
 *
 * Which is what these tests are about: not that three translations exist —
 * other tests cover that — but that the picker takes effect in place.
 */
@OptIn(ExperimentalTestApi::class)
class LanguagePickerTest {
    // `AppEnvironment` sets the JVM default locale, which is process-wide.
    // Left behind, it would decide what language the *next* test reads.
    private val hostLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreTheHostLocale() {
        Locale.setDefault(hostLocale)
    }

    @Test
    fun thePickerSwitchesAllThreeLanguagesInPlace() =
        runComposeUiTest {
            setContent { Picker() }

            // Czech to start with, whatever the host is set to.
            onNodeWithText("Jazyk").assertIsDisplayed()

            onNodeWithText("English").performClick()
            onNodeWithText("Language").assertIsDisplayed()

            onNodeWithText("Українська").performClick()
            onNodeWithText("Мова").assertIsDisplayed()

            onNodeWithText("Čeština").performClick()
            onNodeWithText("Jazyk").assertIsDisplayed()
        }

    @Test
    fun eachLanguageIsOfferedUnderItsOwnName() =
        runComposeUiTest {
            // A Ukrainian captain has to find Ukrainian in a list they
            // cannot otherwise read, so the chips are autonyms and do not
            // change with the language in force.
            setContent { Picker(AppLanguage.UKRAINIAN) }

            onNodeWithText("Čeština").assertIsDisplayed()
            onNodeWithText("English").assertIsDisplayed()
            onNodeWithText("Українська").assertIsDisplayed()
        }

    @Test
    fun everyLanguageSaysInItsOwnWordsThatTheReportDoesNotFollowThePicker() =
        runComposeUiTest {
            // Asserted in all three because a translation file with the
            // wrong language pasted into it is invisible until someone who
            // reads that language holds the phone. It happened once while
            // this was being written, to the Czech file.
            setContent { Picker() }

            onNodeWithText("Zápis pro PSMF je vždy česky.", substring = true).assertIsDisplayed()

            onNodeWithText("English").performClick()
            onNodeWithText("The report for PSMF is always in Czech.", substring = true).assertIsDisplayed()

            onNodeWithText("Українська").performClick()
            onNodeWithText("Протокол для PSMF завжди чеською.", substring = true).assertIsDisplayed()
        }

    @Test
    fun switchingLanguageMidMatchKeepsTheMatch() =
        runComposeUiTest {
            // The language change rebuilds the subtree -- that is how the
            // strings are re-resolved without a restart -- so this is the
            // thing it could plausibly have broken. It does not, because
            // nothing on the console is held in the composition: the score
            // comes from the report and the clock is `now - kickoffAt`.
            var language by mutableStateOf(AppLanguage.CZECH)
            setContent {
                PsmfTheme {
                    AppEnvironment(language) {
                        ConsoleScreen(
                            state = consoleState(),
                            now = KICKOFF + 25.minutes,
                            onEvent = {},
                        )
                    }
                }
            }

            onNodeWithText("25´").assertIsDisplayed()
            onNodeWithText("2:1").assertIsDisplayed()

            language = AppLanguage.UKRAINIAN
            onNodeWithText("Скасувати останнє").assertIsDisplayed()

            // Still the same match, at the same minute.
            onNodeWithText("25´").assertIsDisplayed()
            onNodeWithText("2:1").assertIsDisplayed()
        }

    // -----------------------------------------------------------------
    // Harnesses
    // -----------------------------------------------------------------

    @Composable
    private fun Picker(initial: AppLanguage = AppLanguage.CZECH) {
        var language by remember { mutableStateOf(initial) }
        PsmfTheme {
            AppEnvironment(language) {
                SettingsScreen(
                    state = SettingsUiState(loaded = true, language = language),
                    language = language,
                    onEvent = { event ->
                        if (event is SettingsEvent.LanguageSelected) language = event.language
                    },
                )
            }
        }
    }

    private fun consoleState(): ConsoleUiState =
        ConsoleUiState(
            loading = false,
            entry =
                ConsoleEntry(
                    home =
                        ConsoleTeam(
                            side = TeamSide.HOME,
                            teamName = UiTestData.homeTeam.name,
                            rows =
                                listOf(
                                    ConsoleRow(
                                        appearanceId = AppearanceId("a-novak"),
                                        jerseyNumber = JerseyNumber(9),
                                        name = PlayerName(PersonName.of("Novák"), PersonName.of("Jan")),
                                        dismissed = false,
                                        yellowsInThisMatch = 0,
                                    ),
                                ),
                        ),
                    away = ConsoleTeam(TeamSide.AWAY, UiTestData.awayTeam.name, emptyList()),
                    score = Score(2, 1),
                    kickoffAt = KICKOFF,
                    status = MatchStatus.IN_PROGRESS,
                    log = emptyList(),
                    powerPlays = emptyList(),
                ),
        )
}
