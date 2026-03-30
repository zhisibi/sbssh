package com.sbssh

import android.app.Application

class SbsshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: SbsshApp
            private set
    }
}
