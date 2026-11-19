package com.deva.ledgercalc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deva.ledgercalc.data.ExportManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(viewModel: TapeViewModel) {
    val tape by viewModel.tape.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LedgerCalc", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = tape.rows.isNotEmpty() || tape.draft.hasAmount || tape.archived != null
                    ) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = { confirmClear = true },
                        enabled = tape.rows.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear tape")
                    }
                    IconButton(
                        onClick = { ExportManager.shareText(context, tape) },
                        enabled = tape.rows.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share as text")
                    }
                    IconButton(
                        onClick = { ExportManager.sharePdf(context, tape) },
                        enabled = tape.rows.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Share as PDF")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Bottom
            ) {
                TapeList(
                    tape = tape,
                    onRowClick = viewModel::editRow,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            EntryLine(tape = tape)

            tape.error?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Keypad(
                onDigit = viewModel::digit,
                onDecimal = viewModel::decimal,
                onOperator = viewModel::operator,
                onEquals = viewModel::equals,
                onPercent = viewModel::percent,
                onOpenBracket = viewModel::openBracket,
                onCloseBracket = viewModel::closeBracket,
                onBackspace = viewModel::backspace
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the tape?") },
            text = { Text("Every row will be removed. You can undo this once with the undo button.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                    scope.launch { snackbar.showSnackbar("Tape cleared") }
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}
