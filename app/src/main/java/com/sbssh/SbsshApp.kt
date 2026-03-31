package com.sbssh

import android.app.Application
import cat.ereza.customactivityoncrash.CustomActivityOnCrash

class SbsshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        // CustomActivityOnCrash auto-initializes via manifest intent-filter
    }

    companion object {
        lateinit var instance: SbsshApp
            private set
    }
}
