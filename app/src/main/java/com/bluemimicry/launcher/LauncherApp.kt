package com.bluemimicry.launcher

import android.app.Application
import com.bluemimicry.launcher.data.AppRepository
import com.bluemimicry.launcher.data.LauncherDb
import com.bluemimicry.launcher.data.SettingsStore

/** Tiny manual service locator. Keeps startup cheap and avoids a DI framework dependency. */
class LauncherApp : Application() {
    lateinit var db: LauncherDb; private set
    lateinit var apps: AppRepository; private set
    lateinit var settings: SettingsStore; private set

    override fun onCreate() {
        super.onCreate()
        db = LauncherDb.build(this)
        apps = AppRepository(this)
        settings = SettingsStore(this)
    }
}
