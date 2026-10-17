package com.sonas.actiontracker

import android.app.Application
import java.io.File

/** Saves the stack trace of any crash so the app can show it on the next launch. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { crashFile(this).writeText("${java.util.Date()}\n${e.stackTraceToString()}") }
            previous?.uncaughtException(thread, e)
        }
    }

    companion object {
        fun crashFile(app: Application) = File(app.filesDir, "last_crash.txt")
    }
}
