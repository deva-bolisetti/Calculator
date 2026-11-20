package com.deva.ledgercalc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Tab(val label: String, val icon: ImageVector) {
    CALCULATOR("Calculator", Icons.Filled.Calculate),
    HISTORY("History", Icons.Filled.History)
}

/**
 * Two-tab shell. The calculator keeps its own scaffold and toolbar; history is a plain list, so
 * this only owns the bottom bar and the selected tab.
 *
 * About is not a tab: it is a detail page opened from the calculator toolbar, so it takes over the
 * whole body and hides the bottom bar while it is showing.
 */
@Composable
fun LedgerApp(viewModel: TapeViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.CALCULATOR) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val history by viewModel.history.collectAsStateWithLifecycle()

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                Tab.CALCULATOR -> CalculatorScreen(
                    viewModel = viewModel,
                    onAbout = { showAbout = true }
                )
                Tab.HISTORY -> HistoryScreen(
                    entries = history,
                    onOpen = { id ->
                        viewModel.openFromHistory(id)
                        tab = Tab.CALCULATOR
                    },
                    onDelete = viewModel::deleteHistory
                )
            }
        }
    }
}
