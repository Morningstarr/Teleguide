package com.mongodb.alliance.authorization

import com.mongodb.alliance.channelApp
import io.realm.mongodb.App

interface SignUpListener : App.Callback<Void>{
    fun signUp(username : String, password : String) {
        channelApp.emailPassword.registerUserAsync(username, password, this)
    }
}