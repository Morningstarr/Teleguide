package com.mongodb.alliance.authorization

import com.mongodb.alliance.channelApp
import io.realm.mongodb.App
import io.realm.mongodb.Credentials
import io.realm.mongodb.User

interface SignInListener : App.Callback<User> {
    fun signIn(firstLogin: Boolean, username : String, password : String)
    {
        val creds = Credentials.emailPassword(username, password)
        channelApp.loginAsync(creds, this)
    }
}