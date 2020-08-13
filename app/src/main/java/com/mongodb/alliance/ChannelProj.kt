package com.mongodb.alliance

import android.app.Application
import android.util.Log
import com.mongodb.alliance.BuildConfig.MONGODB_REALM_APP_ID
import io.realm.BuildConfig

import io.realm.Realm
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration

lateinit var channelApp: App

inline fun <reified T> T.TAG(): String = T::class.java.simpleName

class ChannelProj : Application() {

    override fun onCreate() {
        super.onCreate()
        Realm.init(this)
        channelApp = App(AppConfiguration.Builder(MONGODB_REALM_APP_ID).build())

        // Enable more logging in debug mode
        if (BuildConfig.DEBUG) {
            RealmLog.setLevel(LogLevel.ALL)
        }

        Log.v(TAG(), "Initialized the Realm App configuration for: ${channelApp.configuration.appId}")
    }
}