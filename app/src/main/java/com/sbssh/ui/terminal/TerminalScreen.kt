package com.sbssh.ui.terminal


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.sbssh.ui.theme.TerminalBg

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

    val keyboardController = LocalSoftwareKeyboardController.current
    var ctrlMode by remember { mutableStateOf(false) }

    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }
    val termSession = remember {
        TerminalSession(
            "/system/bin/true",
            "/",
            arrayOf("true"),
            emptyArray(),
            object : TerminalSession.SessionChangedCallback {
                override fun onTextChanged(session: TerminalSession) {
                    terminalViewRef.value?.onScreenUpdated()
                }
                override fun onTitleChanged(session: TerminalSession) {}
                override fun onSessionFinished(session: TerminalSession) {}
                override fun onClipboardText(session: TerminalSession, text: String) {}
                override fun onBell(session: TerminalSession) {}
                override fun onColorsChanged(session: TerminalSession) {
                    terminalViewRef.value?.onScreenUpdated()
                }
            }
        )
    }

    val activeTab = uiState.tabs.find { it.id == uiState.activeTabId }

    LaunchedEffect(termSession) {
        viewModel.attachTerminalSession(termSession)

        // Pump input from TerminalSession -> SSH
        withContext(Dispatchers.IO) {
            val queueField = termSession.javaClass.getDeclaredField("mTerminalToProcessIOQueue")
            queueField.isAccessible = true
            val queue = queueField.get(termSession)
            val readMethod = queue.javaClass.getDeclaredMethod("read", ByteArray::class.java, Boolean::class.javaPrimitiveType)
            readMethod.isAccessible = true
            val buffer = ByteArray(4096)
            while (true) {
                val count = readMethod.invoke(queue, buffer, true) as Int
                if (count > 0) {
                    val text = String(buffer, 0, count, Charsets.ISO_8859_1)
                    viewModel.sendRaw(text)
                } else {
                    delay(10)
                }
            }
        }
    }

    val keyButtons = listOf(
        "TAB" to "\t",
        "CTRL" to "",
        "ESC" to "\u001B",
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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
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
                    modifier = Modifier.height(40.dp),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                // Shortcut bar fixed under top bar
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(TerminalBg)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(keyButtons) { (label, code) ->
                        AssistChip(
                            onClick = {
                                if (label == "CTRL") {
                                    ctrlMode = !ctrlMode
                                } else {
                                    viewModel.sendRaw(code)
                                    ctrlMode = false
                                }
                            },
                            label = { Text(if (label == "CTRL" && ctrlMode) "CTRL*" else label, style = MaterialTheme.typography.labelSmall, color = Color.White) },
                            enabled = activeTab?.isConnected == true,
                            modifier = Modifier.height(32.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = Color.White,
                                containerColor = TerminalBg
                            )
                        )
                    }
                }
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
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TerminalBg)
                    .windowInsetsPadding(WindowInsets.ime),
                factory = { context: android.content.Context ->
                    val view = TerminalView(context, null)
                    view.isFocusable = true
                    view.isFocusableInTouchMode = true
                    view.setTextSize(12)
                    view.setOnKeyListener(object : TerminalViewClient {
                        override fun onScale(scale: Float) = 1.0f
                        override fun onSingleTapUp(e: android.view.MotionEvent) {
                            view.requestFocus()
                            keyboardController?.show()
                        }
                        override fun shouldBackButtonBeMappedToEscape() = false
                        override fun copyModeChanged(copyMode: Boolean) {}
                        override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent, session: TerminalSession) = false
                        override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent) = false
                        override fun readControlKey() = ctrlMode
                        override fun readAltKey() = false
                        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false
                        override fun onLongPress(e: android.view.MotionEvent) = false
                    })
                    view.attachSession(termSession)
                    terminalViewRef.value = view
                    view.requestFocus()
                    keyboardController?.show()
                    view
                },
                update = { view: TerminalView ->
                    terminalViewRef.value = view
                    if (view.getCurrentSession() != termSession) {
                        view.attachSession(termSession)
                    }
                }
            )
        }
    }
}
