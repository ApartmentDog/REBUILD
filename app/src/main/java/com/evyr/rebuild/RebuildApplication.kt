package com.evyr.rebuild

import android.app.Application

class RebuildApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Reserved for future global init (e.g. WorkManager configuration
        // for morning/end-of-day summary scheduling).
    }
}
