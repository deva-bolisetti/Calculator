package com.deva.ledgercalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deva.ledgercalc.domain.Amounts
import com.deva.ledgercalc.domain.Tape
import com.deva.ledgercalc.domain.TapeRow

/**
 * The tape: one line per committed value, operator in a fixed left gutter, amounts right-aligned
 * so the digit columns line up. The total sits under a rule, like a printed slip.
 *
 * The gutter and the sign are separate facts. The gutter says what the row does to the total; the
 * amount carries its own minus so a row that subtracts can never be read as a row that adds.
 */
@Composable
fun TapeList(
    tape: Tape,
    onRowClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
) {
    val total = tape.total
    val negative = total.signum() < 0
    val totalText = Amounts.format(total)

    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.Bottom
    ) {
        if (tape.rows.isEmpty()) {
            Text(
                text = "Type a number to start the tape.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        tape.rows.forEach { row ->
            TapeRowView(
                row = row,
                isEditing = tape.draft.editingId == row.id,
                onClick = { onRowClick(row.id) }
            )
        }

        if (tape.rows.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Rule()
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "=",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = totalText,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TapeRowView(row: TapeRow, isEditing: Boolean, onClick: () -> Unit) {
    val amountText = Amounts.format(row.amount)
    val negative = row.amount.signum() < 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Edit this row",
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isEditing) {
                    Modifier.background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 4.dp, horizontal = if (isEditing) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.operator?.symbol ?: " ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )

        if (row.expression != null) {
            Text(
                text = row.expression,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(8.dp))
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = amountText,
            style = MaterialTheme.typography.bodyLarge,
            color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Rule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

/**
 * Entry line: the number being typed, with the operator that will be applied to it.
 *
 * A row correction can be left with [onCancelEdit]; without it the only ways out of edit mode were
 * committing, which is not an escape when the tap that started the edit was a mistake.
 */
@Composable
fun EntryLine(tape: Tape, onCancelEdit: () -> Unit, modifier: Modifier = Modifier) {
    val draft = tape.draft
    val text = when {
        draft.isBracketOpen -> draft.bracketText.orEmpty()
        draft.percent -> draft.digits + "%"
        draft.digits.isEmpty() -> "0"
        else -> draft.digits
    }
    val operator = draft.pendingOperator

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (draft.isEditing) {
                Text(
                    text = "EDIT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                IconButton(onClick = onCancelEdit) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel editing this row",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = operator?.symbol ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.width(22.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = text,
                style = MaterialTheme.typography.displayLarge,
                color = if (draft.digits.isEmpty() && !draft.isBracketOpen) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
