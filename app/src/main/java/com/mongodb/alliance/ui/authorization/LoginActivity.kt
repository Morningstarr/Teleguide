package com.mongodb.alliance.ui.authorization

import android.app.PendingIntent.getActivity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import cafe.adriel.broker.GlobalBroker
import com.mongodb.alliance.databinding.ActivityLoginBinding
import com.mongodb.alliance.ui.authorization.SignInFragment
import com.mongodb.alliance.ui.authorization.SignUpFragment
import kotlinx.coroutines.InternalCoroutinesApi
import org.greenrobot.eventbus.EventBus
import kotlin.time.ExperimentalTime

@ExperimentalTime
@InternalCoroutinesApi
class LoginActivity : AppCompatActivity(), GlobalBroker.Publisher {
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var loginButton: Button
    private lateinit var createUserButton: Button
    private lateinit var binding: ActivityLoginBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actionbar = supportActionBar
        actionbar?.hide()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.buttonEnter.setOnClickListener {
            var bottomSheetFragment =
                SignInFragment()

            bottomSheetFragment.show(
                this.supportFragmentManager,
                bottomSheetFragment.tag
            )
        }
        binding.buttonRegister.setOnClickListener {
            var bottomSheetFragment =
                SignUpFragment()

            bottomSheetFragment.show(
                this.supportFragmentManager,
                bottomSheetFragment.tag
            )
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}


    /*private fun onLoginAfterSignUpSuccess(){
        var intent = Intent(this, ConnectTelegramActivity::class.java)
        intent.putExtra("newAcc", true)
        startActivity(intent)
        finish()
    }

    private fun onLoginSuccess() {
        finish()
    }

    private fun onLoginFailed(errorMsg: String) {
        Timber.e(errorMsg)
        Toast.makeText(baseContext, errorMsg, Toast.LENGTH_LONG).show()
    }*/

    /*private fun login(createUser: Boolean, firstLogin: Boolean) {
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
                    login(false, true)
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
                    if(firstLogin){
                        onLoginAfterSignUpSuccess()
                    }
                    else {
                        onLoginSuccess()
                    }
                }
            }
        }
    }*/

    /*private fun validateCredentials(): Boolean = when {
            username.text.toString().isEmpty() || username.text.length < 3 -> false
            password.text.toString().isEmpty() || password.text.length < 6 -> false
            else -> true
        }
    }*/