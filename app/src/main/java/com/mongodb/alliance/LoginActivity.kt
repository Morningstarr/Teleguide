package com.mongodb.alliance

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import io.realm.mongodb.Credentials

class LoginActivity : AppCompatActivity() {
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var loginButton: Button
    private lateinit var createUserButton: Button

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        username = findViewById(R.id.input_username)
        password = findViewById(R.id.input_password)
        loginButton = findViewById(R.id.button_login)
        createUserButton = findViewById(R.id.button_create)

        loginButton.setOnClickListener { login(false) }
        createUserButton.setOnClickListener { login(true) }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun onLoginSuccess() {
        finish()
    }

    private fun onLoginFailed(errorMsg: String) {
        Log.e(TAG(), errorMsg)
        Toast.makeText(baseContext, errorMsg, Toast.LENGTH_LONG).show()
    }

    private fun login(createUser: Boolean) {
        if (!validateCredentials())
        {
            onLoginFailed("Invalid username or password")
            return
        }

        createUserButton.isEnabled = false
        loginButton.isEnabled = false

        val username = this.username.text.toString()
        val password = this.password.text.toString()


        if (createUser)
        {
            channelApp.emailPasswordAuth.registerUserAsync(username, password) {
                createUserButton.isEnabled = true
                loginButton.isEnabled = true
                if (!it.isSuccess)
                {
                    onLoginFailed("Could not register user.")
                    Log.e(TAG(), "Error: ${it.error}")
                }
                else
                {
                    Log.i(TAG(), "Successfully registered user.")
                    login(false)
                }
            }
        }
        else
        {
            val creds = Credentials.emailPassword(username, password)
            channelApp.loginAsync(creds) {
                loginButton.isEnabled = true
                createUserButton.isEnabled = true
                if (!it.isSuccess)
                {
                    onLoginFailed(it.error.message ?: "An error occurred.")
                }
                else
                {
                    onLoginSuccess()
                }
            }
        }
    }

    private fun validateCredentials(): Boolean = when {
        username.text.toString().isEmpty() || username.text.length < 3 -> false
        password.text.toString().isEmpty() || password.text.length < 6 -> false
        else -> true
    }
}