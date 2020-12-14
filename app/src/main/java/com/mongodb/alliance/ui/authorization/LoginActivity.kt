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
    private lateinit var binding: ActivityLoginBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actionbar = supportActionBar
        actionbar?.hide()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.buttonEnter.setOnClickListener {
            val bottomSheetFragment =
                SignInFragment()

            bottomSheetFragment.show(
                this.supportFragmentManager,
                bottomSheetFragment.tag
            )
        }

        binding.buttonRegister.setOnClickListener {
            val bottomSheetFragment =
                SignUpFragment()

            bottomSheetFragment.show(
                this.supportFragmentManager,
                bottomSheetFragment.tag
            )
        }
    }

    override fun onBackPressed() {
        finish()
    }

}


    /*private fun onLoginAfterSignUpSuccess(){
        var intent = Intent(this, ConnectTelegramActivity::class.java)
        intent.putExtra("newAcc", true)
        startActivity(intent)
        finish()
    }*/