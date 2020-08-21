package com.mongodb.alliance

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import com.mongodb.alliance.databinding.ActivityLoginBinding
import com.mongodb.alliance.databinding.ActivityMainBinding
import com.mongodb.alliance.model.StateChangedEvent
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.ui.telegram.CodeFragment
import com.mongodb.alliance.ui.telegram.PasswordFragment
import com.mongodb.alliance.ui.telegram.PhoneNumberFragment
import io.realm.mongodb.Credentials
import timber.log.Timber

class LoginActivity : AppCompatActivity() {
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var loginButton: Button
    private lateinit var createUserButton: Button
    private lateinit var binding: ActivityLoginBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        username = binding.inputUsername
        password = binding.inputPassword
        loginButton = binding.buttonLogin
        createUserButton = binding.buttonCreate

        loginButton.setOnClickListener { login(false) }
        createUserButton.setOnClickListener { login(true) }

    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun onLoginAfterSignUpSuccess(){
        finish()
    }

    private fun onLoginSuccess() {
        finish()
    }

    private fun onLoginFailed(errorMsg: String) {
        Timber.e(errorMsg)
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
                    Timber.e("Error: ${it.error}")
                }
                else
                {
                    Timber.d("Successfully registered user.")
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