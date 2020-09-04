package com.mongodb.alliance

import android.app.Application
import android.content.Context
import com.mongodb.alliance.BuildConfig.MONGODB_REALM_APP_ID
import com.mongodb.alliance.model.UserRealm
import dagger.hilt.android.HiltAndroidApp
import io.realm.BuildConfig
import io.realm.Realm
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import timber.log.Timber

lateinit var channelApp: App

inline fun <reified T> T.TAG(): String = T::class.java.simpleName

@HiltAndroidApp
class ChannelProj : Application() {

    companion object {
        private lateinit var appContext: Context
        fun getContxt(): Context {
            return appContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        Realm.init(this)
        appContext = applicationContext
        channelApp = App(AppConfiguration.Builder(MONGODB_REALM_APP_ID).build())

        // Enable more logging in debug mode
        if (BuildConfig.DEBUG) {
            RealmLog.setLevel(LogLevel.ALL)
            Timber.plant(Timber.DebugTree())
        }

    }

}