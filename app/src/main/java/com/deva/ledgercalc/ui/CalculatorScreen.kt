package com.deva.ledgercalc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deva.ledgercalc.data.ExportManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(viewModel: TapeViewModel, onAbout: () -> Unit = {}) {
    val tape by viewModel.tape.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // The newest row and the total are the whole point of the tape, so keep them in view. Waiting
    // a frame lets layout settle first, otherwise maxValue is the previous, shorter value. Keying
    // this on the committed row count rather than on the typed digits means reading an earlier row
    // is not interrupted by every keystroke.
    LaunchedEffect(tape.rows.size) {
        withFrameNanos { }
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Back while a row is being corrected means "never mind", not "leave the app".
    BackHandler(enabled = tape.draft.isEditing) { viewModel.cancelEdit() }

    // The tab shell owns the outer layout, so this screen is a plain column with its own toolbar
    // rather than a nested Scaffold, which would apply the system insets twice.
    Column(modifier = Modifier.fillMaxSize()) {
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
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(
                    onClick = { confirmClear = true },
                    enabled = tape.rows.isNotEmpty()
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear tape")
                }
                IconButton(
                    onClick = {
                        if (!ExportManager.shareText(context, tape)) {
                            scope.launch { snackbar.showSnackbar("There is no tape to share yet") }
                        }
                    },
                    enabled = tape.rows.isNotEmpty()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Share as text")
                }
                IconButton(
                    onClick = {
                        // Copy the tape first: the export outlives this frame.
                        val snapshot = tape
                        scope.launch {
                            if (!ExportManager.sharePdf(context, snapshot)) {
                                snackbar.showSnackbar("Could not create the PDF")
                            }
                        }
                    },
                    enabled = tape.rows.isNotEmpty()
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = "Share as PDF")
                }
                IconButton(onClick = onAbout) {
                    Icon(Icons.Filled.Info, contentDescription = "About")
                }
            }
        )

        SnackbarHost(hostState = snackbar)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Bottom
        ) {
            TapeList(
                tape = tape,
                onRowClick = viewModel::editRow,
                modifier = Modifier.fillMaxWidth()
            )
        }

        EntryLine(tape = tape, onCancelEdit = viewModel::cancelEdit)

        tape.error?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::dismissError)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Dismiss",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the tape?") },
            text = { Text("Every row will be removed. You can undo this once, from the snackbar or the undo button.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = "Tape cleared",
                            actionLabel = "Undo",
                            withDismissAction = true
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                    }
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}
