package com.deva.ledgercalc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deva.ledgercalc.domain.Operator

/**
 * Keypad. Layout is fixed rather than responsive: the operator column stays on the right edge so
 * the thumb can reach it, and the size of every key is identical so a fast entry never slips.
 */
@Composable
fun Keypad(
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onOperator: (Operator) -> Unit,
    onEquals: () -> Unit,
    onPercent: () -> Unit,
    onOpenBracket: () -> Unit,
    onCloseBracket: () -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyRow {
                FunctionKey("(", onOpenBracket, Modifier.weight(1f))
                FunctionKey(")", onCloseBracket, Modifier.weight(1f))
                FunctionKey("%", onPercent, Modifier.weight(1f))
                FunctionKey("", onBackspace, Modifier.weight(1f), isIcon = true)
            }
            KeyRow {
                DigitKey('7', onDigit, Modifier.weight(1f))
                DigitKey('8', onDigit, Modifier.weight(1f))
                DigitKey('9', onDigit, Modifier.weight(1f))
                OperatorKey(Operator.DIVIDE, onOperator, Modifier.weight(1f))
            }
            KeyRow {
                DigitKey('4', onDigit, Modifier.weight(1f))
                DigitKey('5', onDigit, Modifier.weight(1f))
                DigitKey('6', onDigit, Modifier.weight(1f))
                OperatorKey(Operator.TIMES, onOperator, Modifier.weight(1f))
            }
            KeyRow {
                DigitKey('1', onDigit, Modifier.weight(1f))
                DigitKey('2', onDigit, Modifier.weight(1f))
                DigitKey('3', onDigit, Modifier.weight(1f))
                OperatorKey(Operator.MINUS, onOperator, Modifier.weight(1f))
            }
            KeyRow {
                DigitKey('0', onDigit, Modifier.weight(1f))
                FunctionKey(".", onDecimal, Modifier.weight(1f))
                EqualsKey(onEquals, Modifier.weight(1f))
                OperatorKey(Operator.PLUS, onOperator, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun DigitKey(digit: Char, onDigit: (Char) -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = { onDigit(digit) },
        modifier = modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(digit.toString(), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun OperatorKey(
    operator: Operator,
    onOperator: (Operator) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onOperator(operator) },
        modifier = modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        )
    ) {
        Text(operator.symbol, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun FunctionKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isIcon: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        if (isIcon) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace"
            )
        } else {
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun EqualsKey(onEquals: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onEquals,
        modifier = modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text("=", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
