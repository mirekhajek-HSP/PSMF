package cz.hspinovace.psmf.ui.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.lineup_absent
import cz.hspinovace.psmf.resources.lineup_added_at_pitch
import cz.hspinovace.psmf.resources.lineup_jersey
import cz.hspinovace.psmf.resources.lineup_no_card
import cz.hspinovace.psmf.resources.lineup_rp_column
import cz.hspinovace.psmf.resources.lineup_suspension_warning
import cz.hspinovace.psmf.ui.format.asFullDate
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.SquadMemberEntry
import org.jetbrains.compose.resources.stringResource

/**
 * One squad member.
 *
 * **The name is the target and tapping it marks the player absent.** Not a
 * checkbox: twelve ticked boxes say "check each of these", which is the
 * job the inversion exists to avoid. An absent row is struck through and
 * dimmed, so three absences are visible from arm's length.
 *
 * The jersey number is a separate target, because it is edited by
 * exception and must not be confused with the tap that marks absence.
 */
@Composable
fun SquadRow(
    member: SquadMemberEntry,
    duplicateNumber: Boolean,
    onEvent: (LineupEvent) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NameAndBadges(
                member = member,
                onEvent = onEvent,
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { onEvent(LineupEvent.AbsenceToggled(member.player.id)) }
                        .padding(vertical = PsmfDimens.itemSpacing),
            )
            if (!member.absent) {
                JerseyNumberField(member, duplicateNumber, onEvent)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun NameAndBadges(
    member: SquadMemberEntry,
    onEvent: (LineupEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
        Text(
            // Surname first, the order the ZoU column is written in.
            text = member.player.name.asWrittenOnReport,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (member.absent) FontWeight.Normal else FontWeight.Medium,
            textDecoration = if (member.absent) TextDecoration.LineThrough else null,
            color =
                if (member.absent) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )

        if (member.absent) {
            Badge(stringResource(Res.string.lineup_absent), MaterialTheme.colorScheme.surfaceVariant)
            return@Column
        }

        IdentificationLine(member, onEvent)
        if (member.addedAtThePitch) {
            Badge(stringResource(Res.string.lineup_added_at_pitch), MaterialTheme.colorScheme.primaryContainer)
        }
        SuspensionBadge(member)
    }
}

/**
 * What will go in the `Číslo RP` column, and the one control that changes
 * it.
 *
 * **The RP number itself is never editable and never offered as an input.**
 * It is issued by PSMF. What the referee can say is that the player did
 * not bring their card, which is the form's own printed rule — and the
 * toggle only appears when saying so would change anything.
 */
@Composable
private fun IdentificationLine(
    member: SquadMemberEntry,
    onEvent: (LineupEvent) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stringResource(Res.string.lineup_rp_column)}: ${member.identification?.value ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (member.cardMakesADifference) {
            TextButton(onClick = { onEvent(LineupEvent.RegistrationCardToggled(member.player.id)) }) {
                Text(stringResource(Res.string.lineup_no_card), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SuspensionBadge(member: SquadMemberEntry) {
    val warning = member.suspensionWarning ?: return
    // Carries its own asOf date, because the count is stale by
    // construction. There is deliberately no badge for the other case:
    // silence is not clearance.
    Badge(
        text =
            stringResource(
                Res.string.lineup_suspension_warning,
                warning.yellowsThisSeason,
                warning.asOf.asFullDate(),
            ),
        container = MaterialTheme.colorScheme.secondaryContainer,
    )
}

@Composable
private fun Badge(
    text: String,
    container: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier =
            Modifier
                .background(color = container, shape = RoundedCornerShape(PsmfDimens.labelGap))
                .padding(horizontal = PsmfDimens.itemSpacing, vertical = PsmfDimens.labelGap),
    )
}

@Composable
private fun JerseyNumberField(
    member: SquadMemberEntry,
    duplicateNumber: Boolean,
    onEvent: (LineupEvent) -> Unit,
) {
    OutlinedTextField(
        value =
            member.jerseyNumber
                ?.value
                ?.toString()
                .orEmpty(),
        onValueChange = { onEvent(LineupEvent.JerseyNumberChanged(member.player.id, it)) },
        label = { Text(stringResource(Res.string.lineup_jersey)) },
        singleLine = true,
        isError = duplicateNumber,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(JERSEY_FIELD_WIDTH).heightIn(min = PsmfDimens.minTouchTarget),
    )
}

private val JERSEY_FIELD_WIDTH = 96.dp
