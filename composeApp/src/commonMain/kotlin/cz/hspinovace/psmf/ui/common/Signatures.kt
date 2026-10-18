package cz.hspinovace.psmf.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cz.hspinovace.psmf.resources.Res
import cz.hspinovace.psmf.resources.signatures_banner
import cz.hspinovace.psmf.resources.signatures_withdraw_body
import cz.hspinovace.psmf.resources.signatures_withdraw_confirm
import cz.hspinovace.psmf.resources.signatures_withdraw_keep
import cz.hspinovace.psmf.resources.signatures_withdraw_title
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import org.jetbrains.compose.resources.stringResource

// What the signatures attest, and the two ways the referee is told that a
// change takes them back (prompt 13, part 4): a question before the change is
// made, where there is one moment to ask, and a notice where there is not.

/**
 * **Asked before the change is saved**: the report is signed, and this change
 * touches something the signatures attest, so they will be taken back and the
 * recap will ask for them again. Where there is a moment to ask -- a goal, a
 * card, a whistle, the "no cards" affirmation -- this is it.
 */
@Composable
fun WithdrawSignaturesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.signatures_withdraw_title)) },
        text = { Text(stringResource(Res.string.signatures_withdraw_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.signatures_withdraw_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.signatures_withdraw_keep)) }
        },
    )
}

/**
 * **A notice, where a screen saves as you type.** The header and the lineups
 * write through on every change, so there is no single moment to ask; the
 * notice is on the screen before the first edit, and the first edit takes the
 * signatures back.
 */
@Composable
fun SignedBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = stringResource(Res.string.signatures_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(PsmfDimens.itemSpacing),
        )
    }
}
