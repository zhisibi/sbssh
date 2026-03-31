package com.sbssh.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sbssh.data.crypto.CryptoManager
import com.sbssh.data.db.AppDatabase
import com.sbssh.data.db.VpsEntity
import com.sbssh.util.BiometricHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val language: String = "zh",
    val fontSize: String = "medium",
    val showAbout: Boolean = false,
    val showLanguageDialog: Boolean = false,
    val showFontSizeDialog: Boolean = false,
    val error: String? = null,
    val success: String? = null
)

class SettingsViewModel(private val context: Context) : ViewModel() {

    private val cryptoManager = CryptoManager(context)
    private val prefs = context.getSharedPreferences("sbssh_settings", Context.MODE_PRIVATE)
    private var dao = runCatching { AppDatabase.getInstance().vpsDao() }.getOrNull()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val biometricAvailable = BiometricHelper.isBiometricAvailable(
            context as AppCompatActivity
        )
        _uiState.value = _uiState.value.copy(
            biometricEnabled = cryptoManager.isBiometricEnabled(),
            biometricAvailable = biometricAvailable,
            language = prefs.getString("language", "zh") ?: "zh",
            fontSize = prefs.getString("font_size", "medium") ?: "medium"
        )
    }

    fun toggleBiometric() {
        val current = _uiState.value.biometricEnabled
        if (current) {
            cryptoManager.disableBiometric()
            _uiState.value = _uiState.value.copy(biometricEnabled = false)
        } else {
            // Need to enter master password to enable
            _uiState.value = _uiState.value.copy(
                error = "Enter your master password to enable biometric"
            )
        }
    }

    fun enableBiometricFromPassword(password: String) {
        try {
            val salt = cryptoManager.getSalt()
            val keyBytes = cryptoManager.deriveKey(password, salt)
            cryptoManager.enableBiometric(keyBytes)
            _uiState.value = _uiState.value.copy(biometricEnabled = true, error = null)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    fun showLanguageDialog() {
        _uiState.value = _uiState.value.copy(showLanguageDialog = true)
    }

    fun dismissLanguageDialog() {
        _uiState.value = _uiState.value.copy(showLanguageDialog = false)
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("language", lang).apply()
        _uiState.value = _uiState.value.copy(
            language = lang,
            showLanguageDialog = false,
            success = if (lang == "zh") "语言已切换为中文" else "Language switched to English"
        )
    }

    fun showFontSizeDialog() {
        _uiState.value = _uiState.value.copy(showFontSizeDialog = true)
    }

    fun dismissFontSizeDialog() {
        _uiState.value = _uiState.value.copy(showFontSizeDialog = false)
    }

    fun setFontSize(size: String) {
        prefs.edit().putString("font_size", size).apply()
        _uiState.value = _uiState.value.copy(
            fontSize = size,
            showFontSizeDialog = false,
            success = "Font size: $size"
        )
    }

    fun backupServers() {
        viewModelScope.launch {
            try {
                if (dao == null) {
                    _uiState.value = _uiState.value.copy(error = "Database not initialized")
                    return@launch
                }
                val vpsList = dao!!.getAllVpsAsList()
                val backupList = vpsList.map { v ->
                    mapOf(
                        "alias" to v.alias,
                        "host" to v.host,
                        "port" to v.port,
                        "username" to v.username,
                        "authType" to v.authType,
                        "encryptedPassword" to v.encryptedPassword,
                        "encryptedKeyContent" to v.encryptedKeyContent,
                        "encryptedKeyPassphrase" to v.encryptedKeyPassphrase,
                        "createdAt" to v.createdAt,
                        "updatedAt" to v.updatedAt
                    )
                }
                val gson = Gson()
                val json = gson.toJson(backupList)

                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "sbssh_backup_${sdf.format(Date())}.json"

                val dir = File(context.getExternalFilesDir(null), "backups")
                dir.mkdirs()
                val file = File(dir, fileName)
                file.writeText(json)

                _uiState.value = _uiState.value.copy(
                    success = "Backup saved: ${file.absolutePath}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Backup failed: ${e.message}")
            }
        }
    }

    fun restoreServers(uri: Uri) {
        viewModelScope.launch {
            try {
                if (dao == null) {
                    _uiState.value = _uiState.value.copy(error = "Database not initialized")
                    return@launch
                }
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: throw Exception("Failed to read backup file")

                val gson = Gson()
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val backupList: List<Map<String, Any>> = gson.fromJson(json, type)

                val now = System.currentTimeMillis()
                var count = 0
                for (item in backupList) {
                    val vps = VpsEntity(
                        alias = item["alias"] as? String ?: "Unknown",
                        host = item["host"] as? String ?: "0.0.0.0",
                        port = (item["port"] as? Double)?.toInt() ?: 22,
                        username = item["username"] as? String ?: "root",
                        authType = item["authType"] as? String ?: "PASSWORD",
                        encryptedPassword = item["encryptedPassword"] as? String,
                        encryptedKeyContent = item["encryptedKeyContent"] as? String,
                        encryptedKeyPassphrase = item["encryptedKeyPassphrase"] as? String,
                        createdAt = (item["createdAt"] as? Double)?.toLong() ?: now,
                        updatedAt = (item["updatedAt"] as? Double)?.toLong() ?: now
                    )
                    dao!!.insertVps(vps)
                    count++
                }

                _uiState.value = _uiState.value.copy(
                    success = "Restored $count server(s)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Restore failed: ${e.message}")
            }
        }
    }

    fun showAbout() {
        _uiState.value = _uiState.value.copy(showAbout = true)
    }

    fun dismissAbout() {
        _uiState.value = _uiState.value.copy(showAbout = false)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, success = null)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(context) as T
        }
    }
}
