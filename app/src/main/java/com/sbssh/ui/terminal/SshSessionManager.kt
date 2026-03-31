package com.sbssh.ui.terminal

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.sbssh.util.AppLogger
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

class SshSessionManager {

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var outputStream: OutputStream? = null
    private val jsch = JSch()

    var isConnected: Boolean = false
        private set

    var onDataReceived: ((String) -> Unit)? = null
    var onDisconnected: ((String) -> Unit)? = null

    private var readJob: Job? = null

    suspend fun connect(
        host: String,
        port: Int,
        username: String,
        authType: String,
        password: String?,
        keyContent: String?,
        keyPassphrase: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            jsch.removeAllIdentity()

            if (authType == "KEY" && keyContent != null) {
                val identityName = "sbssh_key_${host}_${port}"
                jsch.addIdentity(
                    identityName,
                    keyContent.toByteArray(),
                    null,
                    keyPassphrase?.toByteArray()
                )
            }

            session = jsch.getSession(username, host, port)
            if (authType == "PASSWORD" && password != null) {
                session?.setPassword(password)
            }

            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            session?.setConfig(config)
            session?.connect(15000)

            channel = session?.openChannel("shell") as? ChannelShell
            channel?.setPtyType("xterm-256color", 80, 24, 0, 0)
            channel?.connect(5000)

            outputStream = channel?.outputStream
            isConnected = true

            readJob = CoroutineScope(Dispatchers.IO).launch {
                val inputStream: InputStream = channel?.inputStream ?: return@launch
                val buffer = ByteArray(4096)
                while (isConnected && channel?.isConnected == true) {
                    try {
                        if (inputStream.available() > 0) {
                            val bytesRead = inputStream.read(buffer)
                            if (bytesRead > 0) {
                                val data = String(buffer, 0, bytesRead)
                                withContext(Dispatchers.Main) {
                                    onDataReceived?.invoke(data)
                                }
                            }
                        } else {
                            delay(50)
                        }
                    } catch (e: Exception) {
                        if (isConnected) {
                            withContext(Dispatchers.Main) {
                                onDisconnected?.invoke("Connection error: ${e.message}")
                            }
                        }
                        break
                    }
                }
                if (isConnected) {
                    withContext(Dispatchers.Main) {
                        onDisconnected?.invoke("Connection closed")
                    }
                }
                isConnected = false
            }

            true
        } catch (e: Exception) {
            isConnected = false
            withContext(Dispatchers.Main) {
                onDisconnected?.invoke("Connection failed: ${e.message}")
            }
            false
        }
    }

    fun sendCommand(command: String) {
        try {
            if (outputStream == null) {
                AppLogger.log("SSH", "sendCommand: outputStream is null")
                return
            }
            outputStream?.write(command.toByteArray())
            outputStream?.flush()
        } catch (e: Exception) {
            AppLogger.log("SSH", "sendCommand failed", e)
        }
    }

    fun resize(cols: Int, rows: Int) {
        try {
            (channel as? ChannelShell)?.setPtySize(cols, rows, cols * 8, rows * 8)
        } catch (_: Exception) { }
    }

    fun disconnect() {
        isConnected = false
        readJob?.cancel()
        try { channel?.disconnect() } catch (_: Exception) { }
        try { session?.disconnect() } catch (_: Exception) { }
        channel = null
        session = null
        outputStream = null
    }
}
