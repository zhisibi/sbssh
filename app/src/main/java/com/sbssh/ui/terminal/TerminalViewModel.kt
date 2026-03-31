package com.sbssh.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sbssh.data.crypto.FieldCryptoManager
import com.sbssh.data.crypto.SessionKeyHolder
import com.sbssh.data.db.AppDatabase
import com.sbssh.data.db.VpsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TerminalTab(
    val id: Int,
    val vpsAlias: String,
    val output: String = "",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null
)

data class TerminalUiState(
    val tabs: List<TerminalTab> = emptyList(),
    val activeTabId: Int = -1,
    val commandInput: String = ""
)

class TerminalViewModel(private val vpsId: Long) : ViewModel() {

    private var dao = runCatching { AppDatabase.getInstance().vpsDao() }.getOrNull()
    private val sessions = mutableMapOf<Int, SshSessionManager>()
    private var tabCounter = 0
    private var terminalSession: com.termux.terminal.TerminalSession? = null

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var vps: VpsEntity? = null

    init {
        if (dao == null) {
            _uiState.value = _uiState.value.copy(
                tabs = listOf(TerminalTab(id = 0, vpsAlias = "Error", error = "Database not initialized")),
                activeTabId = 0
            )
        } else {
            viewModelScope.launch {
                vps = dao!!.getVpsById(vpsId)
                vps?.let { addTab(it) }
            }
        }
    }

    fun addTab(vpsEntity: VpsEntity? = null) {
        val v = vpsEntity ?: vps ?: return
        val tabId = tabCounter++
        val newTab = TerminalTab(
            id = tabId,
            vpsAlias = v.alias,
            isConnecting = true
        )
        _uiState.value = _uiState.value.copy(
            tabs = _uiState.value.tabs + newTab,
            activeTabId = tabId
        )

        val manager = SshSessionManager()
        sessions[tabId] = manager

        manager.onDataReceived = { data ->
            // Feed Termux terminal session if attached
            terminalSession?.write(data.toByteArray(), 0, data.toByteArray().size)

            val tabs = _uiState.value.tabs.toMutableList()
            val idx = tabs.indexOfFirst { it.id == tabId }
            if (idx >= 0) {
                tabs[idx] = tabs[idx].copy(output = tabs[idx].output + data)
                _uiState.value = _uiState.value.copy(tabs = tabs)
            }
        }

        manager.onDisconnected = { reason ->
            val tabs = _uiState.value.tabs.toMutableList()
            val idx = tabs.indexOfFirst { it.id == tabId }
            if (idx >= 0) {
                tabs[idx] = tabs[idx].copy(isConnected = false, isConnecting = false, error = reason)
                _uiState.value = _uiState.value.copy(tabs = tabs)
            }
        }

        viewModelScope.launch {
            try {
                val key = SessionKeyHolder.get()
                val crypto = FieldCryptoManager()
                val success = manager.connect(
                    host = v.host,
                    port = v.port,
                    username = v.username,
                    authType = v.authType,
                    password = crypto.decrypt(v.encryptedPassword, key),
                    keyContent = crypto.decrypt(v.encryptedKeyContent, key),
                    keyPassphrase = crypto.decrypt(v.encryptedKeyPassphrase, key)
                )
                val tabs = _uiState.value.tabs.toMutableList()
                val idx = tabs.indexOfFirst { it.id == tabId }
                if (idx >= 0) {
                    tabs[idx] = tabs[idx].copy(
                        isConnected = success,
                        isConnecting = false,
                        error = if (!success) "Connection failed" else null
                    )
                    _uiState.value = _uiState.value.copy(tabs = tabs)
                }
            } catch (e: Exception) {
                val tabs = _uiState.value.tabs.toMutableList()
                val idx = tabs.indexOfFirst { it.id == tabId }
                if (idx >= 0) {
                    tabs[idx] = tabs[idx].copy(
                        isConnected = false,
                        isConnecting = false,
                        error = e.message ?: "Connection failed"
                    )
                    _uiState.value = _uiState.value.copy(tabs = tabs)
                }
            }
        }
    }

    fun switchTab(tabId: Int) {
        _uiState.value = _uiState.value.copy(activeTabId = tabId)
    }

    fun closeTab(tabId: Int) {
        sessions[tabId]?.disconnect()
        sessions.remove(tabId)
        val tabs = _uiState.value.tabs.filter { it.id != tabId }
        val activeId = if (_uiState.value.activeTabId == tabId) {
            tabs.lastOrNull()?.id ?: -1
        } else {
            _uiState.value.activeTabId
        }
        _uiState.value = _uiState.value.copy(tabs = tabs, activeTabId = activeId)
    }

    fun sendCommand(command: String) {
        val tabId = _uiState.value.activeTabId
        sessions[tabId]?.sendCommand(command + "\r")
    }

    fun sendRaw(data: String) {
        val tabId = _uiState.value.activeTabId
        sessions[tabId]?.sendCommand(data)
    }

    fun updateCommandInput(input: String) {
        _uiState.value = _uiState.value.copy(commandInput = input)
    }

    fun attachTerminalSession(session: com.termux.terminal.TerminalSession) {
        terminalSession = session
    }

    override fun onCleared() {
        super.onCleared()
        sessions.values.forEach { it.disconnect() }
        sessions.clear()
    }

    class Factory(private val vpsId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TerminalViewModel(vpsId) as T
        }
    }
}
