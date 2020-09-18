package com.mongodb.alliance.ui.telegram

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cafe.adriel.broker.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.events.PhoneChangedEvent
import com.mongodb.alliance.R
import com.mongodb.alliance.databinding.ActivityConnectTelegramBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.StateChangedEvent
import com.mongodb.alliance.events.TelegramConnectedEvent
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.Telegram
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class ConnectTelegramActivity : AppCompatActivity(), GlobalBroker.Subscriber {

    @TelegramServ
    @Inject
    lateinit var t_service: Service
    private lateinit var code: EditText
    private var bottomSheetFragment: BottomSheetDialogFragment? = null

    private lateinit var binding: ActivityConnectTelegramBinding
    private var newAccount by Delegates.notNull<Boolean>()

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        newAccount = intent.getBooleanExtra("newAcc", false)

        val actionbar = supportActionBar
        if (actionbar != null) {
            actionbar.title = "Connect telegram"
        }

        binding = ActivityConnectTelegramBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        otherEventsSubscription()
        telegramConnectedSubscription()

        if (newAccount) {
            showDialog()
        }


        lifecycleScope.launch {
            val task = async {
                withContext(Dispatchers.IO) {
                    (t_service as TelegramService).returnClientState()
                }
            }
            val state = task.await()
            when (state) {
                ClientState.ready -> {
                    val task = async {
                        (t_service as TelegramService).getPhoneNumber()
                    }
                    val number = task.await()
                    binding.labelNumber.text = number
                    actionbar?.setDisplayHomeAsUpEnabled(true)
                }
                ClientState.waitNumber, ClientState.waitPassword, ClientState.waitCode -> {
                    if(!newAccount){
                        (t_service as TelegramService).initService()
                    }
                }
                else -> {

                }
            }
        }

        binding.fab.setOnClickListener { view ->
            val btf = bottomSheetFragment
            if (btf != null) {
                btf.show(supportFragmentManager, btf.tag)
            } else {
                Toast.makeText(baseContext, "Nothing to show", Toast.LENGTH_SHORT).show()
            }
        }

        binding.connTgResetNumber.setOnClickListener { view ->
            lifecycleScope.launch {
                val task = async {
                    withContext(Dispatchers.IO) {
                        (t_service as TelegramService).returnClientState()
                    }
                }

                val state = task.await()
                if (state == ClientState.waitCode || state == ClientState.waitPassword) {
                    binding.labelNumber.text = getString(R.string.no_telephone_number_connected)
                    (t_service as TelegramService).resetPhoneNumber()
                } else {
                    Toast.makeText(
                        baseContext,
                        "You can't reset your number now",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.connTgResetTelegramAccount.setOnClickListener { view ->
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Alert")
            builder.setMessage("Are you sure you want to change Telegram Account?")

            builder.setPositiveButton(android.R.string.yes) { dialog, which ->

                binding.labelNumber.text = getString(R.string.no_telephone_number_connected)
                lifecycleScope.launch {
                    showLoading(true)
                    while (getRetained<StateChangedEvent>() != null) {
                        removeRetained<StateChangedEvent>()
                    }
                    withContext(Dispatchers.IO) {
                        (t_service as TelegramService).logOut()
                        (t_service as TelegramService).changeAccount()
                    }
                    showLoading(false)

                }
            }

            builder.setNegativeButton(android.R.string.no) { dialog, which ->

            }

            builder.show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.skip, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_skip -> {
                finish()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun telegramConnectedSubscription() {
        subscribe<TelegramConnectedEvent>(lifecycleScope, emitRetained = true) { event ->
            if(newAccount){
                finish()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unsubscribe()
    }

    private fun otherEventsSubscription() {
        subscribe<StateChangedEvent>(lifecycleScope, emitRetained = true) { event ->
            Timber.d("State changed")
            when (event.clientState) {
                ClientState.waitNumber -> {
                    //bottomSheetFragment?.dismiss()
                    bottomSheetFragment =
                        PhoneNumberFragment()

                    (bottomSheetFragment as PhoneNumberFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as PhoneNumberFragment).tag
                    )
                }
                ClientState.waitCode -> {
                    //bottomSheetFragment?.dismiss()
                    bottomSheetFragment =
                        CodeFragment()

                    (bottomSheetFragment as CodeFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as CodeFragment).tag
                    )
                }
                ClientState.waitPassword -> {
                    //bottomSheetFragment?.dismiss()
                    bottomSheetFragment =
                        PasswordFragment()
                    (bottomSheetFragment as PasswordFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as PasswordFragment).tag
                    )
                }
                else -> {

                }
            }
            showLoading(false)
        }

        subscribe<PhoneChangedEvent>(lifecycleScope) { event ->
            binding.labelNumber.text = event.newNumber
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }


    private fun showLoading(show: Boolean) {
        if (show) {
            binding.connTgProgress.visibility = View.VISIBLE
            binding.connTgResetTelegramAccount.isEnabled = false
            binding.connTgResetNumber.isEnabled = false
        } else {
            binding.connTgProgress.visibility = View.GONE
            binding.connTgResetTelegramAccount.isEnabled = true
            binding.connTgResetNumber.isEnabled = true
        }
    }

    @ExperimentalCoroutinesApi
    private fun showDialog(){
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Alert")
        lifecycleScope.launch {
            val task = async {
                withContext(Dispatchers.IO) {
                    (t_service as TelegramService).returnClientState()
                }
            }

            val state = task.await()
            when(state){
                ClientState.waitCode -> {
                    //Toast.makeText(baseContext, "waitCode", Toast.LENGTH_SHORT).show()
                    builder.setMessage("Continue telegram connection or add new telegram account?")
                    builder.setPositiveButton(R.string.cont) { dialog, which ->
                        lifecycleScope.launch {
                            (t_service as TelegramService).initService()
                        }
                    }
                    builder.setNegativeButton(R.string.add_new) { dialog, which ->
                        lifecycleScope.launch {
                            (t_service as TelegramService).logOut()
                            (t_service as TelegramService).initService()
                        }
                    }
                    builder.show()
                }
                ClientState.waitPassword->
                {
                    //Toast.makeText(baseContext, "waitPassword", Toast.LENGTH_SHORT).show()
                    builder.setMessage("Continue telegram connection or add new telegram account?")
                    builder.setPositiveButton(R.string.cont) { dialog, which ->
                        lifecycleScope.launch {
                            (t_service as TelegramService).initService()
                        }
                    }
                    builder.setNegativeButton(R.string.add_new) { dialog, which ->
                        lifecycleScope.launch {
                            showLoading(true)
                            withContext(Dispatchers.IO) {
                                (t_service as TelegramService).logOut()
                                (t_service as TelegramService).changeAccount()
                            }
                            numberRefresh()
                            showLoading(false)
                        }
                    }
                    builder.show()
                }
                ClientState.waitNumber -> {
                    (t_service as TelegramService).initService()
                }
                ClientState.ready -> {
                    //Toast.makeText(baseContext, "ready", Toast.LENGTH_SHORT).show()
                    builder.setMessage("Continue with connected telegram account?")
                    builder.setPositiveButton(R.string.cont) { dialog, which ->
                        finish()
                    }
                    builder.setNegativeButton(R.string.add_new) { dialog, which ->
                        lifecycleScope.launch {
                            showLoading(true)
                            withContext(Dispatchers.IO) {
                                (t_service as TelegramService).logOut()
                                (t_service as TelegramService).changeAccount()
                            }
                            numberRefresh()
                            showLoading(false)
                        }
                    }
                    builder.show()
                }
                ClientState.setParameters -> {
                    //Toast.makeText(baseContext, "setParameters", Toast.LENGTH_SHORT).show()
                }
                ClientState.waitParameters -> {
                    //Toast.makeText(baseContext, "waitParameters", Toast.LENGTH_SHORT).show()
                    (t_service as TelegramService).setUpClient()
                    showDialog()
                }
                ClientState.closed -> {
                    //Toast.makeText(baseContext, "closed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun numberRefresh(){
        lateinit var number : String
        lifecycleScope.launch {
            val task = async {
                (t_service as TelegramService).getPhoneNumber()
            }
            number = task.await()
        }

        if(number == ""){
            binding.labelNumber.text = "No telephone number connected"
        }
        else{
            binding.labelNumber.text = number
        }
    }

}

