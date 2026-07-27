package io.github.ukemeikot.flicksoccer

import android.app.Application
import android.content.Context

/** Holds the application context for platform services (haptics) that need it. */
object AndroidAppContext {
    @Volatile
    var context: Context? = null
}

class FlickSoccerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidAppContext.context = applicationContext
    }
}
