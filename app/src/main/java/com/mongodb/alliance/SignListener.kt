package com.mongodb.alliance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import com.mongodb.alliance.ui.telegram.ConnectTelegramActivity
import io.realm.mongodb.Credentials
import kotlinx.coroutines.InternalCoroutinesApi
import timber.log.Timber
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

    fun login(createUser: Boolean, firstLogin: Boolean, username : String, password : String,
        activity: Activity) {
        /*if (!validateCredentials(username, password, activity))
        {
            onLoginFailed("Invalid username or password", activity)
            return
        }*/

        //createUserButton.isEnabled = false
        //loginButton.isEnabled = false

        if (createUser)
        {
            channelApp.emailPasswordAuth.registerUserAsync(username, password) {
                //createUserButton.isEnabled = true
                //loginButton.isEnabled = true
                if (!it.isSuccess)
                {
                    onLoginFailed("Could not register user.", activity)
                    Timber.e("Error: ${it.error}")
                }
                else
                {
                    Timber.d("Successfully registered user.")
                    login(false, true, username, password, activity)
                }
            }
        }
        else
        {
            val creds = Credentials.emailPassword(username, password)
            channelApp.loginAsync(creds) {
                //loginButton.isEnabled = true
                //createUserButton.isEnabled = true
                if (!it.isSuccess)
                {
                    onLoginFailed(it.error.message ?: "An error occurred.", activity)
                }
                else
                {
                    /*if(firstLogin){
                        //onLoginAfterSignUpSuccess(activity)
                        onLoginSuccess(activity)
                    }
                    else {
                        onLoginSuccess(activity)
                    }*/
                    activity.finish()
                }
            }
        }
    }

}