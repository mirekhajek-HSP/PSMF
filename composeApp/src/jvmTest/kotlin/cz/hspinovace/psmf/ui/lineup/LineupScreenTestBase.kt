package cz.hspinovace.psmf.ui.lineup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import cz.hspinovace.psmf.domain.AppearanceId
import cz.hspinovace.psmf.domain.DisciplinaryRecord
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.Player
import cz.hspinovace.psmf.domain.PlayerId
import cz.hspinovace.psmf.domain.PlayerName
import cz.hspinovace.psmf.domain.RpNumber
import cz.hspinovace.psmf.domain.TeamSide
import cz.hspinovace.psmf.ui.UiTestData
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import cz.hspinovace.psmf.usecase.LineupEntry
import cz.hspinovace.psmf.usecase.NewPlayerRequest
import cz.hspinovace.psmf.usecase.SquadMemberEntry
import cz.hspinovace.psmf.usecase.TeamLineupEntry
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The people and the state the lineup screen's tests are built from, in one place so
 * that the tests can be split by what they are about.
 */
@OptIn(ExperimentalTestApi::class)
abstract class LineupScreenTestBase {
    protected fun player(
        ref: String,
        surname: String,
        first: String,
        number: Int,
        yellows: Int? = null,
        rp: RpNumber? = null,
        born: LocalDate? = null,
    ) = Player(
        id = PlayerId(ref),
        ref = ref,
        teamId = UiTestData.homeTeamId,
        name = PlayerName(PersonName.of(surname), PersonName.of(first)),
        rpNumber = rp,
        dateOfBirth = born,
        birthNumber = null,
        defaultJerseyNumber = JerseyNumber(number),
        discipline = yellows?.let { DisciplinaryRecord(it, LocalDate(2026, 8, 24)) },
    )

    protected val novak = player("novak", "Novák", "Jan", 9)
    protected val poupe = player("poupe", "Poupě", "Petr", 11)

    /** Two yellows: on an even total, so the advisory badge shows. */
    protected val kriz = player("kriz", "Kříž", "Ondřej", 18, yellows = 2)

    /** Three yellows: odd, so no badge -- which is NOT clearance. */
    protected val odd = player("odd", "Růžička", "Radek", 27, yellows = 3)

    protected fun member(
        p: Player,
        present: Boolean = false,
        card: String = "",
        cardPresent: Boolean = true,
    ) = SquadMemberEntry(
        player = p,
        appearanceId = AppearanceId("app-${p.ref}"),
        present = present,
        registrationCardPresent = cardPresent,
    ).withCardNumberTyped(card)

    protected fun homeTeam(
        members: List<SquadMemberEntry>,
        captain: PlayerId? = null,
        goalkeeper: PlayerId? = null,
        confirmation: LineupConfirmation? = null,
    ) = TeamLineupEntry(
        side = TeamSide.HOME,
        team = UiTestData.homeTeam,
        members = members,
        kitId = UiTestData.homeTeam.primaryKit.id,
        captain = captain,
        goalkeeper = goalkeeper,
        confirmation = confirmation,
    )

    protected fun state(
        members: List<SquadMemberEntry> = listOf(member(novak), member(poupe), member(kriz)),
        captain: PlayerId? = null,
        goalkeeper: PlayerId? = null,
        confirmation: LineupConfirmation? = null,
        newPlayer: NewPlayerRequest? = null,
        rejected: Boolean = false,
    ): LineupUiState {
        val away =
            TeamLineupEntry(
                side = TeamSide.AWAY,
                team = UiTestData.awayTeam,
                members = emptyList(),
                kitId = UiTestData.awayTeam.primaryKit.id,
            )
        return LineupUiState(
            loading = false,
            entry = LineupEntry(homeTeam(members, captain, goalkeeper, confirmation), away),
            newPlayer = newPlayer,
            newPlayerRejected = rejected,
        )
    }
}
