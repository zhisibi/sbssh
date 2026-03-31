package com.sbssh

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sbssh.ui.navigation.NavGraph
import com.sbssh.ui.settings.SettingsManager
import com.sbssh.ui.theme.SbsshTheme
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsManager = SettingsManager.getInstance(this)

        // Apply language on activity creation
        val lang = settingsManager.settings.value.language
        val locale = if (lang == "en") Locale.ENGLISH else Locale.CHINESE
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        setContent {
            val settings by settingsManager.settings.collectAsState()
            SbsshTheme(
                fontScale = settings.fontScale
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph()
                }
            }
        }
    }
}
