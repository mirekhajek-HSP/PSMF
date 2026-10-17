package cz.hspinovace.psmf.ui.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.lineup_added_at_pitch
import cz.hspinovace.psmf.resources.lineup_captain_label
import cz.hspinovace.psmf.resources.lineup_card_number_hint
import cz.hspinovace.psmf.resources.lineup_date_of_birth
import cz.hspinovace.psmf.resources.lineup_date_of_birth_hint
import cz.hspinovace.psmf.resources.lineup_dob_from_file
import cz.hspinovace.psmf.resources.lineup_goalkeeper_label
import cz.hspinovace.psmf.resources.lineup_has_card
import cz.hspinovace.psmf.resources.lineup_jersey
import cz.hspinovace.psmf.resources.lineup_no_card
import cz.hspinovace.psmf.resources.lineup_rp_column
import cz.hspinovace.psmf.resources.lineup_rp_from_league
import cz.hspinovace.psmf.resources.lineup_suspension_warning
import cz.hspinovace.psmf.ui.common.PsmfTextField
import cz.hspinovace.psmf.ui.format.asFullDate
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.usecase.SquadMemberEntry
import org.jetbrains.compose.resources.stringResource

/**
 * One squad member.
 *
 * **The name is the target and tapping it marks the player as having come**,
 * and tapping again takes the mark back (DECISIONS 2026-10-17). Not a
 * checkbox to hunt for: the whole name area is the control, and the mark at
 * its left says what it did.
 *
 * **Every row is the same height, marked or not.** The referee works down a
 * list of up to twenty-five names with a thumb, and a row that opened and
 * pushed the rest down would move the next name out from under it after every
 * tap. So an unmarked row lays out the very controls a marked one does -- same
 * fields, same button -- but disabled and invisible, and marking it is a
 * change of what is drawn, never of where anything is. (Drawn and not merely
 * given a reserved height, because a reserved height is right at one font
 * scale and wrong at the next.)
 *
 * The one exception is deliberate: a row asking for a date of birth shows what
 * it read back under the field, which is a line taller. That is the referee
 * working in that row, with a thumb on it.
 */
@Composable
fun SquadRow(
    member: SquadMemberEntry,
    duplicateNumber: Boolean,
    onEvent: (LineupEvent) -> Unit,
    isCaptain: Boolean = false,
    isGoalkeeper: Boolean = false,
    missingIdentification: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NameAndMark(
                member = member,
                isCaptain = isCaptain,
                isGoalkeeper = isGoalkeeper,
                onToggle = { onEvent(LineupEvent.PresenceToggled(member.player.id)) },
                modifier = Modifier.weight(1f),
            )
            JerseyNumberField(member, duplicateNumber, onEvent)
        }
        IdentificationLine(member, missingIdentification, onEvent)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

/** The mark, the name and what is known about the player whatever the referee has done. */
@Composable
private fun NameAndMark(
    member: SquadMemberEntry,
    isCaptain: Boolean,
    isGoalkeeper: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .toggleable(value = member.present, role = Role.Checkbox, onValueChange = { onToggle() })
                .heightIn(min = PsmfDimens.minTouchTarget)
                .padding(vertical = PsmfDimens.labelGap),
        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresenceMark(member.present)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PsmfDimens.labelGap)) {
            Text(
                // Surname first, the order the ZoU column is written in.
                text = member.player.name.asWrittenOnReport,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (member.present) FontWeight.Medium else FontWeight.Normal,
                color =
                    if (member.present) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Roles(isCaptain, isGoalkeeper)
            if (member.addedAtThePitch) {
                Badge(stringResource(Res.string.lineup_added_at_pitch), MaterialTheme.colorScheme.primaryContainer)
            }
            SuspensionBadge(member)
        }
    }
}

/** Filled and ticked when the player came; an empty ring until then. */
@Composable
private fun PresenceMark(present: Boolean) {
    Box(
        modifier =
            Modifier
                .size(MARK_SIZE)
                .clip(CircleShape)
                .then(
                    if (present) {
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        Modifier.border(MARK_RING, MaterialTheme.colorScheme.outline, CircleShape)
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (present) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(MARK_TICK),
            )
        }
    }
}

/**
 * Which of the two roles this player has, in words. Under the name, in the
 * line the name column already has room for, so naming a captain never moves
 * a row.
 */
@Composable
private fun Roles(
    isCaptain: Boolean,
    isGoalkeeper: Boolean,
) {
    if (!isCaptain && !isGoalkeeper) return
    val text =
        listOfNotNull(
            stringResource(Res.string.lineup_captain_label).takeIf { isCaptain },
            stringResource(Res.string.lineup_goalkeeper_label).takeIf { isGoalkeeper },
        ).joinToString(" · ")
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * What goes in the `Číslo RP` column, and the one control that changes how.
 *
 * **Four cases, one place.** A league RP number is shown and never editable;
 * with a card and none from the league, the referee copies the number from
 * the card; with no card, the date of birth is the league's own if it has
 * one and the referee's if it does not (the form's printed rule). The button
 * says which way the row is going to turn.
 *
 * **What the referee types here is what was written that day.** It is the
 * appearance's, never the player's, and nothing on this screen presents it as
 * PSMF's record.
 *
 * Laid out for every row, marked or not, so that marking does not move
 * anything; see [SquadRow].
 */
@Composable
private fun IdentificationLine(
    member: SquadMemberEntry,
    missingIdentification: Boolean,
    onEvent: (LineupEvent) -> Unit,
) {
    val enabled = member.present
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = IDENTIFICATION_HEIGHT)
                .hiddenUnless(enabled),
        horizontalArrangement = Arrangement.spacedBy(PsmfDimens.itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when {
                member.showsLeagueRp -> {
                    FixedValue(stringResource(Res.string.lineup_rp_from_league, member.identification?.value.orEmpty()))
                }

                member.asksForCardNumber -> {
                    CardNumberField(member, missingIdentification, enabled, onEvent)
                }

                member.asksForDateOfBirth -> {
                    DateOfBirthField(member, missingIdentification, enabled, onEvent)
                }

                else -> {
                    FixedValue(stringResource(Res.string.lineup_dob_from_file, member.dateOfBirthOnFileWritten()))
                }
            }
        }
        TextButton(
            onClick = { onEvent(LineupEvent.RegistrationCardToggled(member.player.id)) },
            enabled = enabled,
            modifier = Modifier.heightIn(min = PsmfDimens.minTouchTarget),
        ) {
            Text(
                text =
                    stringResource(
                        if (member.registrationCardPresent) Res.string.lineup_no_card else Res.string.lineup_has_card,
                    ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** A value the league supplied: shown, and nothing to tap. */
@Composable
private fun FixedValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = PsmfDimens.itemSpacing),
    )
}

/** The league record's own date of birth, as people write it. */
private fun SquadMemberEntry.dateOfBirthOnFileWritten(): String = player.dateOfBirth?.asFullDate().orEmpty()

/**
 * The number copied from the player's card, for a player with no RP number
 * from the league. Digits; the length is the reader's business
 * ([cz.hspinovace.psmf.usecase.parseCardNumber]) and a number it cannot use is
 * marked, not quietly dropped.
 */
@Composable
private fun CardNumberField(
    member: SquadMemberEntry,
    missingIdentification: Boolean,
    enabled: Boolean,
    onEvent: (LineupEvent) -> Unit,
) {
    PsmfTextField(
        value = member.cardNumberTyped,
        onValueChange = { onEvent(LineupEvent.CardNumberTyped(member.player.id, it)) },
        label = { Text(stringResource(Res.string.lineup_rp_column)) },
        // Always one line, as the date-of-birth field has: so the row is the
        // same height whichever of the two it asks for, and a number it
        // cannot use is said in words and not only drawn red.
        supportingText = { Text(stringResource(Res.string.lineup_card_number_hint)) },
        isError = member.cardNumberRejected || missingIdentification,
        keyboardType = KeyboardType.Number,
        // Each row is its own little edit; the next field down the list is
        // another player's.
        imeAction = ImeAction.Done,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
    )
}

/**
 * The date of birth, for a player with no card and none on file.
 *
 * On this screen, in this row, because this is where the referee marks them
 * and the captain is standing beside them. The referee types the date as
 * people write it and the app renders the column's YYMMDD; the parsed date is
 * echoed back, so `18.5.1929` is visible as 1929 before it reaches the report.
 * Offered from the date remembered on this device, if there is one; editable,
 * and never required to match it.
 */
@Composable
private fun DateOfBirthField(
    member: SquadMemberEntry,
    missingIdentification: Boolean,
    enabled: Boolean,
    onEvent: (LineupEvent) -> Unit,
) {
    val parsed = member.typedDateOfBirth
    PsmfTextField(
        value = member.dateOfBirthTyped,
        onValueChange = { onEvent(LineupEvent.DateOfBirthTyped(member.player.id, it)) },
        label = { Text(stringResource(Res.string.lineup_date_of_birth)) },
        supportingText = {
            Text(parsed?.asFullDate() ?: stringResource(Res.string.lineup_date_of_birth_hint))
        },
        isError = missingIdentification || (member.dateOfBirthTyped.isNotBlank() && parsed == null),
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = PsmfDimens.minTouchTarget),
    )
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
    PsmfTextField(
        value =
            member.jerseyNumber
                ?.value
                ?.toString()
                .orEmpty(),
        onValueChange = { onEvent(LineupEvent.JerseyNumberChanged(member.player.id, it)) },
        label = { Text(stringResource(Res.string.lineup_jersey)) },
        isError = duplicateNumber,
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
        enabled = member.present,
        modifier =
            Modifier
                .width(JERSEY_FIELD_WIDTH)
                .heightIn(min = PsmfDimens.minTouchTarget)
                .hiddenUnless(member.present),
    )
}

/**
 * Drawn only when [shown]: invisible, and out of the accessibility tree,
 * otherwise -- but laid out either way. See [SquadRow].
 */
private fun Modifier.hiddenUnless(shown: Boolean): Modifier = if (shown) this else alpha(0f).clearAndSetSemantics { }

private val JERSEY_FIELD_WIDTH = 96.dp
private val MARK_SIZE = 28.dp
private val MARK_TICK = 18.dp
private val MARK_RING = 2.dp

/**
 * Tall enough for the field and for a button that wraps to two lines at a
 * large font. A minimum, like every size here; see [PsmfDimens].
 */
private val IDENTIFICATION_HEIGHT = 64.dp
