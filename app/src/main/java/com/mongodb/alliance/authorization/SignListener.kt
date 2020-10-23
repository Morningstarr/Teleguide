package com.mongodb.alliance.authorization

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import com.mongodb.alliance.ui.telegram.ConnectTelegramActivity
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.time.ExperimentalTime

@ExperimentalTime
@InternalCoroutinesApi
interface SignListener {

    fun onLoginFailed(message: String, activity : Activity){
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    fun onLoginAfterSignUpSuccess(activity : Activity){
        val intent = Intent(activity, ConnectTelegramActivity::class.java)
        intent.putExtra("newAcc", true)
        startActivity(activity, intent, null)
        activity.finish()
    }

    fun onLoginSuccess(activity : Activity){
        activity.finish()
    }

    fun validateCredentials(email : String, password : String, activity : Activity) : Boolean{
        if(email.isEmpty() || email.length < 3){
            onLoginFailed("Your email is incorrect!", activity)
            return false
        }
        if(password.isEmpty() || password.length <= 6){
            onLoginFailed("Your password is incorrect! (must contain more than 6 symbols)", activity)
            return false
        }
        return true
    }

}