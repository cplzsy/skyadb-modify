package com.sky22333.skyadb

import android.app.Application
import com.sky22333.skyadb.adb.AdbIdentityManager
import timber.log.Timber

class AdbManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AppServices.initialize(this)
        // Identity configuration is immediate; key loading/generation stays off the UI thread.
        AdbIdentityManager.initialize(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
