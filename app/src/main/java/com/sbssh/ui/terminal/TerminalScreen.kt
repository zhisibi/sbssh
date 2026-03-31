package com.sbssh.ui.terminal


import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val keyButtons = listOf(
        "ESC" to "\u001B",
        "TAB" to "\t",
        "CTRL+A" to "\u0001",
        "CTRL+E" to "\u0005",
        "CTRL+C" to "\u0003",
        "CTRL+Z" to "\u001A",
        "CTRL+L" to "\u000C",
        "ALT" to "\u001B",
        "↑" to "\u001B[A",
        "↓" to "\u001B[B",
        "←" to "\u001B[D",
        "→" to "\u001B[C",
        "HOME" to "\u001B[H",
        "END" to "\u001B[F",
        "PGUP" to "\u001B[5~",
        "PGDN" to "\u001B[6~",
        "DEL" to "\u001B[3~",
        "ENTER" to "\r"
    )

    val focusRequester = remember { FocusRequester() }
    var inputBuffer by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        topBar = {
            if (!imeVisible) {
                TopAppBar(
                    title = {
                        val activeTab = uiState.tabs.find { it.id == uiState.activeTabId }
                        Text(activeTab?.vpsAlias ?: "Terminal", maxLines = 1)
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
                    modifier = Modifier.fillMaxWidth(0.33f),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
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
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        })
                    }
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
                        Text(
                            "Connecting...",
                            color = TerminalGreen,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
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

                // Hidden input field to capture keyboard typing
                BasicTextField(
                    value = inputBuffer,
                    onValueChange = { new ->
                        val old = inputBuffer
                        if (new.length > old.length) {
                            val add = new.substring(old.length)
                            viewModel.sendRaw(add)
                        } else if (new.length < old.length) {
                            val count = old.length - new.length
                            if (count > 0) viewModel.sendRaw("\b".repeat(count))
                        }
                        inputBuffer = new
                        if (inputBuffer.length > 32) inputBuffer = ""
                    },

                    modifier = Modifier
                        .size(1.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(color = Color.Transparent),
                    cursorBrush = SolidColor(Color.Transparent)
                )
            }

            // Shortcut bar (single row), floats above IME
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(keyButtons) { (label, code) ->
                    AssistChip(
                        onClick = { viewModel.sendRaw(code) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        enabled = activeTab?.isConnected == true
                    )
                }
            }
        }
    }
}

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
