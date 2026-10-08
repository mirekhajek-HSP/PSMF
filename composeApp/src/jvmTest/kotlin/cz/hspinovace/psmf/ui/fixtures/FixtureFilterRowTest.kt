package cz.hspinovace.psmf.ui.fixtures

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.DateWindow
import cz.hspinovace.psmf.usecase.FilterOptions
import cz.hspinovace.psmf.usecase.FixtureFilter
import cz.hspinovace.psmf.usecase.ListFixtures
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The filter control: select boxes for league, group, pitch and dates, a
 * text field for the team (DECISIONS 2026-10-08).
 *
 * The options are built from [LeagueSix] -- twelve groups and 43 pitches,
 * league 6's size -- plus one group of a seventh league, so a change of
 * league has somewhere to go. The cascade as a referee drives it, through
 * the ViewModel, is `FixturesFilterFlowTest`'s; what the filter does to the
 * list is `shared`'s `FixtureListAtLeagueScaleTest`.
 */
@OptIn(ExperimentalTestApi::class)
class FixtureFilterRowTest {
    @Test
    fun theClosedFilterSaysWhatIsChosenAndOffersNoGroupYet() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Filter(FixtureFilter()) }

                onNodeWithText("Liga").assertTextContains("Všechny ligy")
                onNodeWithText("Hřiště").assertTextContains("Všechna hřiště")
                onNodeWithText("Termín").assertTextContains("Nadcházející")
                // No league chosen, so no group to choose.
                onNodeWithText("Skupina").assertDoesNotExist()
                // The default is not a filter to clear.
                onNodeWithText("Zrušit filtr").assertDoesNotExist()
            }
        }

    @Test
    fun choosingALeagueReportsItWithNoGroup() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent { Filter(FixtureFilter(), onFilterChanged = { chosen = it }) }

                choose("Liga", "6. liga")
            }
            assertEquals(6, chosen?.leagueLevel)
            assertNull(chosen?.groupId)
        }

    @Test
    fun aChosenLeagueRevealsItsGroupsWholeLeagueFirst() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent { Filter(FixtureFilter(leagueLevel = 6), onFilterChanged = { chosen = it }) }

                onNodeWithText("Skupina").assertTextContains("Celá 6. liga")
                onNodeWithText("Skupina").performClick()
                waitForIdle()
                ('A'..'L').forEach { onNodeWithText("6. liga $it").assertExists() }
                // The other league's group is not offered under this one.
                onNodeWithText("7. liga A").assertDoesNotExist()
                onNodeWithText("6. liga K").performScrollTo().performClick()
            }
            assertEquals(6, chosen?.leagueLevel)
            assertEquals(LeagueSix.group('K').group.id, chosen?.groupId)
        }

    @Test
    fun changingTheLeagueClearsTheGroupAndAPitchTheNewLeagueDoesNotPlayAt() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            val pitch = LeagueSix.pitchesOf('K').first()
            withLanguage("cs") {
                setContent {
                    Filter(
                        FixtureFilter(leagueLevel = 6, groupId = LeagueSix.group('K').group.id, venue = pitch),
                        onFilterChanged = { chosen = it },
                    )
                }

                choose("Liga", "7. liga")
            }
            assertEquals(7, chosen?.leagueLevel)
            assertNull(chosen?.groupId, "changing the league did not clear the group")
            assertNull(chosen?.venue, "a pitch 7. liga never plays at was kept")
        }

    @Test
    fun thePitchSelectOffersOnlyThePitchesInScopeByCodeAndName() =
        runComposeUiTest {
            val sixK = LeagueSix.group('K').group.id
            withLanguage("cs") {
                setContent { Filter(FixtureFilter(leagueLevel = 6, groupId = sixK)) }

                onNodeWithText("Hřiště").performClick()
                waitForIdle()

                LeagueSix.pitchesOf('K').forEach { code ->
                    val pitch = LeagueSix.pitches.single { it.code == code }
                    // Code and name in one option.
                    onNodeWithText(pitch.name!!).assertTextContains(code.value)
                }
                // Group A's pitches, and the ones nobody plays at, are not offered.
                (LeagueSix.pitchesOf('A') - LeagueSix.pitchesOf('K')).forEach { code ->
                    onNodeWithText(LeagueSix.pitches.single { it.code == code }.name!!).assertDoesNotExist()
                }
                onNodeWithText("Hřiště číslo 43").assertDoesNotExist()
            }
        }

    @Test
    fun everyOptionIsAFullWidthRowBigEnoughForAColdThumb() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Filter(FixtureFilter(leagueLevel = 6), modifier = Modifier.width(NARROW_PHONE)) }

                val field = sizeOf("Skupina")
                onNodeWithText("Skupina").performClick()
                waitForIdle()

                // The layout's own size, not what the menu's scroll leaves
                // visible of it: the rows further down are the same rows.
                ('A'..'L').forEach { letter ->
                    val option = sizeOf("6. liga $letter")
                    assertTrue(option.height >= PsmfDimens.minTouchTarget, "6. liga $letter is ${option.height} tall")
                    assertTrue(
                        option.width >= field.width - 1.dp,
                        "6. liga $letter is ${option.width} of ${field.width}",
                    )
                }
            }
        }

    @Test
    fun theDatesSelectOffersFourWindowsAndMarksTheChosenOne() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent { Filter(FixtureFilter(), onFilterChanged = { chosen = it }) }

                onNodeWithText("Termín").performClick()
                waitForIdle()
                listOf("Dnes", "Odehrané", "Všechny termíny").forEach { onNodeWithText(it).assertExists() }
                onNode(hasText("Nadcházející") and isSelected()).assertExists()
                onNodeWithText("Odehrané").performClick()
            }
            assertEquals(DateWindow.PLAYED, chosen?.dates)
        }

    @Test
    fun typingATeamNameReportsIt() =
        runComposeUiTest {
            // A text field, not a select: a hundred and forty-four teams is
            // a list to search, not to scroll.
            var chosen: FixtureFilter? = null
            withLanguage("cs") {
                setContent { Filter(FixtureFilter(), onFilterChanged = { chosen = it }) }

                onNodeWithText("Tým").performTextInput("Kom")
            }
            assertEquals("Kom", chosen?.teamQuery)
        }

    @Test
    fun clearingResetsEverythingAndTakesTheDatesBackToUpcoming() =
        runComposeUiTest {
            var chosen: FixtureFilter? = null
            val current =
                FixtureFilter(
                    leagueLevel = 6,
                    groupId = LeagueSix.group('K').group.id,
                    venue = LeagueSix.pitchesOf('K').first(),
                    teamQuery = "Kom",
                    dates = DateWindow.PLAYED,
                )
            withLanguage("cs") {
                setContent { Filter(current, onFilterChanged = { chosen = it }) }

                onNodeWithText("Zrušit filtr").performClick()
            }
            assertEquals(FixtureFilter(), chosen)
            assertEquals(DateWindow.UPCOMING, chosen?.dates)
        }

    @Test
    fun aDateAloneIsAFilterToClear() =
        runComposeUiTest {
            withLanguage("cs") {
                setContent { Filter(FixtureFilter(dates = DateWindow.ALL)) }

                onNodeWithText("Zrušit filtr").assertExists()
            }
        }

    @Test
    fun theLabelsTranslateAndTheDataDoesNot() =
        runComposeUiTest {
            withLanguage("uk") {
                setContent { Filter(FixtureFilter(leagueLevel = 6, groupId = LeagueSix.group('K').group.id)) }

                onNodeWithText("Ліга").assertTextContains("6. liga")
                onNodeWithText("Група").assertTextContains("6. liga K")
                onNodeWithText("Поле").assertTextContains("Усі поля")
                onNodeWithText("Коли").assertTextContains("Майбутні")
                onNodeWithText("Команда").assertExists()
            }
        }

    // -----------------------------------------------------------------
    // Harness
    // -----------------------------------------------------------------

    @Composable
    private fun Filter(
        filter: FixtureFilter,
        onFilterChanged: (FixtureFilter) -> Unit = {},
        modifier: Modifier = Modifier,
    ) {
        PsmfTheme {
            FixtureFilterRow(filter = filter, options = options, onFilterChanged = onFilterChanged, modifier = modifier)
        }
    }

    /** Opens the select labelled [label] and taps [option] in it. */
    private fun ComposeUiTest.choose(
        label: String,
        option: String,
    ) {
        onNodeWithText(label).performClick()
        waitForIdle()
        onNodeWithText(option).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.sizeOf(text: String): DpSize {
        val size = onNodeWithText(text).fetchSemanticsNode().size
        return with(density) { DpSize(size.width.toDp(), size.height.toDp()) }
    }

    private companion object {
        /** A narrow phone's width. The filter is padded inside it, as on screen. */
        val NARROW_PHONE = 360.dp

        val options: FilterOptions =
            runBlocking {
                ListFixtures(
                    InMemoryLeague(LeagueSix.groups + LeagueSix.seventh),
                    GatedMatchRepository(),
                    PinnedClock(LeagueSix.today),
                    TimeZone.UTC,
                )().options
            }
    }
}
