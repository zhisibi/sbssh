package com.sbssh.ui.terminal

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sbssh.ui.theme.TerminalBg
import com.sbssh.ui.theme.TerminalGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    vpsId: Long,
    onBack: () -> Unit
) {
    val viewModel: TerminalViewModel = viewModel(
        factory = TerminalViewModel.Factory(vpsId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val quickCommands = listOf("ls", "cd ..", "pwd", "top", "df -h", "free -h", "ps aux", "clear")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val activeTab = uiState.tabs.find { it.id == uiState.activeTabId }
                    Text(activeTab?.vpsAlias ?: "Terminal")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.addTab() }) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab bar
            if (uiState.tabs.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.tabs.indexOfFirst { it.id == uiState.activeTabId }.coerceAtLeast(0),
                    edgePadding = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.tabs.forEach { tab ->
                        Tab(
                            selected = tab.id == uiState.activeTabId,
                            onClick = { viewModel.switchTab(tab.id) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        tab.vpsAlias,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.closeTab(tab.id) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close",
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Terminal output area
            val activeTab = uiState.tabs.find { it.id == uiState.activeTabId }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TerminalBg)
            ) {
                when {
                    activeTab == null -> {
                        Text(
                            "No active session",
                            color = TerminalGreen,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    activeTab.isConnecting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = TerminalGreen
                        )
                    }
                    else -> {
                        TerminalOutput(
                            output = activeTab.output,
                            isConnected = activeTab.isConnected,
                            error = activeTab.error,
                            onInput = { viewModel.sendRaw(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Quick commands
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(quickCommands) { cmd ->
                    AssistChip(
                        onClick = { viewModel.sendCommand(cmd) },
                        label = { Text(cmd, style = MaterialTheme.typography.labelSmall) },
                        enabled = activeTab?.isConnected == true
                    )
                }
            }

            // Command input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = uiState.commandInput,
                    onValueChange = viewModel::updateCommandInput,
                    textStyle = TextStyle(
                        color = TerminalGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(TerminalGreen),
                    modifier = Modifier
                        .weight(1f)
                        .background(TerminalBg)
                        .padding(8.dp),
                    decorationBox = {
                        if (uiState.commandInput.isEmpty()) {
                            Text("Type command...", color = TerminalGreen.copy(alpha = 0.5f), fontSize = 14.sp)
                        }
                        it()
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (uiState.commandInput.isNotBlank()) {
                            viewModel.sendCommand(uiState.commandInput)
                            viewModel.updateCommandInput("")
                        }
                    },
                    enabled = activeTab?.isConnected == true
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = TerminalGreen)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TerminalOutput(
    output: String,
    isConnected: Boolean,
    error: String?,
    onInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Using a simple scrollable text view for terminal output
    // In a production app, you'd use a proper terminal emulator
    Box(modifier = modifier) {
        val scrollState = rememberScrollState()

        LaunchedEffect(output.length) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .verticalScroll(scrollState)
        ) {
            if (error != null && !isConnected) {
                Text(
                    error,
                    color = Color.Red,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }

            // ANSI-stripped output display
            val cleanOutput = output.replace(Regex("\u001B\\[[;\\d]*m"), "")
            Text(
                cleanOutput,
                color = TerminalGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                softWrap = true
            )

            if (isConnected) {
                Text(
                    "█",
                    color = TerminalGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }
    }
}
