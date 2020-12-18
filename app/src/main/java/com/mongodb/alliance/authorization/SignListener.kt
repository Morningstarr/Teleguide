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

    fun validateCredentials(email : String, password : String, activity : Activity) : Boolean{
        if(email.isEmpty() || email.length < 3 || !isEmail(email)){
            onLoginFailed("Your email is incorrect!", activity)
            return false
        }
        if(password.isEmpty() || password.length <= 6){
            onLoginFailed("Your password is incorrect! (must contain more than 6 symbols)", activity)
            return false
        }
        return true
    }

    private fun isEmail(email : String):Boolean{
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

}