package com.sbssh

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import cat.ereza.customactivityoncrash.CustomActivityOnCrash

class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stackTrace = CustomActivityOnCrash.getStackTraceFromIntent(intent) ?: "No stack trace"
        val activityLog = CustomActivityOnCrash.getActivityLogFromIntent(intent) ?: "No activity log"

        val textView = TextView(this).apply {
            text = buildString {
                append("sbssh Debug Crash Report\n\n")
                append("=== Stack Trace ===\n")
                append(stackTrace)
                append("\n\n=== Activity Log ===\n")
                append(activityLog)
            }
            textSize = 12f
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        setContentView(scrollView)
    }
}
