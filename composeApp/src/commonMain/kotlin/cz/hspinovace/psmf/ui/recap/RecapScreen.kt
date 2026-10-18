package cz.hspinovace.psmf.ui.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cz.hspinovace.psmf.domain.ConfirmingParty
import cz.hspinovace.psmf.export.ZouResult
import cz.hspinovace.psmf.export.ZouText
import cz.hspinovace.psmf.export.ZouTimes
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.recap_confirm_as
import cz.hspinovace.psmf.resources.recap_confirm_away_captain
import cz.hspinovace.psmf.resources.recap_confirm_home_captain
import cz.hspinovace.psmf.resources.recap_confirm_referee
import cz.hspinovace.psmf.resources.recap_confirmed_by
import cz.hspinovace.psmf.resources.recap_continue
import cz.hspinovace.psmf.resources.recap_document
import cz.hspinovace.psmf.resources.recap_document_note
import cz.hspinovace.psmf.resources.recap_full_time
import cz.hspinovace.psmf.resources.recap_half_time
import cz.hspinovace.psmf.resources.recap_loading
import cz.hspinovace.psmf.resources.recap_no_captain
import cz.hspinovace.psmf.resources.recap_no_cards
import cz.hspinovace.psmf.resources.recap_no_cards_confirmed
import cz.hspinovace.psmf.resources.recap_no_cards_note
import cz.hspinovace.psmf.resources.recap_no_cards_title
import cz.hspinovace.psmf.resources.recap_no_cards_withdraw
import cz.hspinovace.psmf.resources.recap_no_referee
import cz.hspinovace.psmf.resources.recap_result
import cz.hspinovace.psmf.resources.recap_result_note
import cz.hspinovace.psmf.resources.recap_signatures
import cz.hspinovace.psmf.ui.common.ActionRow
import cz.hspinovace.psmf.ui.common.PrimaryAction
import cz.hspinovace.psmf.ui.common.Section
import cz.hspinovace.psmf.ui.common.WithdrawSignaturesDialog
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 6.
 *
 * **The document, not a summary.** On paper a captain signs one sheet they
 * can see in full; here they confirm a screen, and whatever is not on it
 * is not being checked (analysis section 5.5). So the middle of this
 * screen is the report itself, rendered by the same formatter the export
 * uses — the captains confirm exactly the bytes that leave the phone.
 *
 * **Nothing on it is typed** (prompt 13, part 4): the result is the goals',
 * and the three signatures are one tap each, under a name this screen already
 * knows and shows on the button.
 */
@Composable
fun RecapScreen(
    state: RecapUiState,
    onEvent: (RecapEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val report = state.report
        if (state.loading || report == null) {
            Text(
                text = stringResource(Res.string.recap_loading),
                modifier = Modifier.fillMaxWidth().padding(PsmfDimens.screenPadding),
            )
            return@Surface
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(PsmfDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(PsmfDimens.sectionSpacing),
        ) {
            report.result?.let { ResultSection(it) }

            Section(
                title = stringResource(Res.string.recap_document),
                note = stringResource(Res.string.recap_document_note),
            ) {
                // Monospaced, because it is the report and not a summary of
                // one: the columns are the form's columns.
                Text(
                    text = ZouText.format(report),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            CardsAffirmation(state, onEvent)
            SignatureSection(state, onEvent)

            ActionRow {
                PrimaryAction(
                    text = stringResource(Res.string.recap_continue),
                    onClick = { onEvent(RecapEvent.ContinuePressed) },
                )
            }
        }
    }

    // Asked before the change is made: it takes the signatures back.
    if (state.withdrawing != null) {
        WithdrawSignaturesDialog(
            onConfirm = { onEvent(RecapEvent.WithdrawalConfirmed) },
            onDismiss = { onEvent(RecapEvent.WithdrawalDismissed) },
        )
    }
}

/**
 * `poločas` and `Konečný výsledek`: shown, and **not editable**. They come
 * from the goals, and a line says where a goal is corrected.
 */
@Composable
private fun ResultSection(result: ZouResult) {
    Section(
        title = stringResource(Res.string.recap_result),
        note = stringResource(Res.string.recap_result_note),
    ) {
        ScoreRow(stringResource(Res.string.recap_half_time), result.halfTime)
        ScoreRow(stringResource(Res.string.recap_full_time), result.fullTime)
    }
}

@Composable
private fun ScoreRow(
    label: String,
    score: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(score, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * `políčka proškrtne` — the referee striking the boxes through.
 *
 * Only offered while nothing has been recorded. An empty block and an
 * affirmed "no cards" look the same on a screen and mean different things
 * to PSMF, which is the whole reason the distinction is modelled. **It can be
 * taken back**: a mis-tap is not final, and a block that is not accounted for
 * is what the readiness check then asks about.
 */
@Composable
private fun CardsAffirmation(
    state: RecapUiState,
    onEvent: (RecapEvent) -> Unit,
) {
    if (state.noCardsAffirmed) {
        Section(title = stringResource(Res.string.recap_no_cards_title)) {
            Text(
                text = stringResource(Res.string.recap_no_cards_confirmed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(
                onClick = { onEvent(RecapEvent.NoCardsWithdrawn) },
                modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
            ) {
                Text(stringResource(Res.string.recap_no_cards_withdraw))
            }
        }
        return
    }
    if (!state.cardsUnaccountedFor) return

    Section(
        title = stringResource(Res.string.recap_no_cards_title),
        note = stringResource(Res.string.recap_no_cards_note),
    ) {
        OutlinedButton(
            onClick = { onEvent(RecapEvent.NoCardsAffirmed) },
            modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
        ) {
            Text(stringResource(Res.string.recap_no_cards))
        }
    }
}

/**
 * The three final confirmations: home captain, away captain, referee.
 *
 * **One tap each, under the name on the button** -- the captain marked in that
 * team's lineup, the referee from the header -- with nothing to type and no
 * deputy to switch on. Once given, it reads the name and the time. Where
 * there is no name to sign under there is no button, only what to do about it.
 */
@Composable
private fun SignatureSection(
    state: RecapUiState,
    onEvent: (RecapEvent) -> Unit,
) {
    Section(title = stringResource(Res.string.recap_signatures)) {
        ConfirmingParty.entries.forEach { party ->
            val given = state.confirmed[party]
            val name = state.signsAs[party]
            when {
                given != null -> {
                    Text(
                        text =
                            "${party.label()}: " +
                                stringResource(
                                    Res.string.recap_confirmed_by,
                                    given.confirmedBy.value,
                                    ZouTimes.written(given.at),
                                ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                name != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
                        Text(party.label(), style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(
                            onClick = { onEvent(RecapEvent.ConfirmationGiven(party)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
                        ) {
                            Text(stringResource(Res.string.recap_confirm_as, name))
                        }
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
                        Text(party.label(), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = party.missingName(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmingParty.label(): String =
    when (this) {
        ConfirmingParty.HOME_CAPTAIN -> stringResource(Res.string.recap_confirm_home_captain)
        ConfirmingParty.AWAY_CAPTAIN -> stringResource(Res.string.recap_confirm_away_captain)
        ConfirmingParty.REFEREE -> stringResource(Res.string.recap_confirm_referee)
    }

@Composable
private fun ConfirmingParty.missingName(): String =
    when (this) {
        ConfirmingParty.REFEREE -> stringResource(Res.string.recap_no_referee)
        else -> stringResource(Res.string.recap_no_captain)
    }
