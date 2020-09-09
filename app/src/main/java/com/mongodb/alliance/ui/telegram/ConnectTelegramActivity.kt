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
import com.mongodb.alliance.events.RegistrationCompletedEvent
import com.mongodb.alliance.events.StateChangedEvent
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class ConnectTelegramActivity : AppCompatActivity(), GlobalBroker.Subscriber {

    @TelegramServ
    @Inject lateinit var t_service: Service
    private lateinit var code: EditText
    private var bottomSheetFragment : BottomSheetDialogFragment? = null

    private lateinit var binding: ActivityConnectTelegramBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actionbar = supportActionBar
        if (actionbar != null) {
            actionbar.title = "Connect telegram"
        }

        binding = ActivityConnectTelegramBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        otherEventsSubscription()
        registrationCompletedSubscription()
        lifecycleScope.launch {
            val task = async {
                //unsubscribe()
                withContext(Dispatchers.IO) {
                    (t_service as TelegramService).returnClientState()
                }
            }

            val state = task.await()
            when(state){
                ClientState.ready ->{
                    val task = async {
                        (t_service as TelegramService).getPhoneNumber()
                    }
                    val number = task.await()
                    binding.labelNumber.text = number
                }
                ClientState.waitParameters ->{
                    withContext(Dispatchers.IO) {
                        (t_service as TelegramService).setUpClient()
                    }
                    val task = async {
                        (t_service as TelegramService).getPhoneNumber()
                    }
                    val number = task.await()
                    binding.labelNumber.text = number
                }
                ClientState.waitNumber ->{
                    withContext(Dispatchers.IO) {
                        t_service.initService()
                    }
                }
                ClientState.waitPassword ->{
                    withContext(Dispatchers.IO) {
                        t_service.initService()
                    }
                }
                ClientState.waitCode ->{
                    withContext(Dispatchers.IO) {
                        t_service.initService()
                    }
                }
            }
        }

        binding.fab.setOnClickListener { view ->
            val btf = bottomSheetFragment
            if(btf != null) {
                btf.show(supportFragmentManager, btf.tag)
            }
            else{
                Toast.makeText(baseContext, "Nothing to show", Toast.LENGTH_SHORT).show()
            }
        }

        binding.connTgResetNumber.setOnClickListener { view ->
            lifecycleScope.launch {
                /*val task = async {
                    (t_service as TelegramService).getPhoneNumber()
                }
                val number = task.await()

                if(number != ""){
                    binding.labelNumber.text = getString(R.string.no_telephone_number_connected)
                    (t_service as TelegramService).resetPhoneNumber()
                }
                else{
                    Toast.makeText(baseContext, "You can't reset your number now", Toast.LENGTH_SHORT).show()
                }*/
                unsubscribe()
                val task = async {
                    //unsubscribe()
                    withContext(Dispatchers.IO) {
                        (t_service as TelegramService).returnClientState()
                    }
                }

                val state = task.await()
                if(state == ClientState.waitCode || state == ClientState.waitPassword){
                    binding.labelNumber.text = getString(R.string.no_telephone_number_connected)
                    (t_service as TelegramService).resetPhoneNumber()
                }
                else{
                    Toast.makeText(baseContext, "You can't reset your number now", Toast.LENGTH_SHORT).show()
                }
                otherEventsSubscription()
                registrationCompletedSubscription()
            }
        }

        binding.connTgResetTelegramAccount.setOnClickListener { view ->
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Alert")
            builder.setMessage("Are you sure you want change Telegram Account?")

            builder.setPositiveButton(android.R.string.yes) { dialog, which ->

                binding.labelNumber.text = getString(R.string.no_telephone_number_connected)
                lifecycleScope.launch {
                    showLoading(true)
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

    private fun registrationCompletedSubscription(){
        subscribe<RegistrationCompletedEvent>(lifecycleScope, emitRetained = true) { event ->
            if(event.clientState == ClientState.completed){
                removeRetained<RegistrationCompletedEvent>()
                finish()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unsubscribe()
    }

    private fun otherEventsSubscription(){
        subscribe<StateChangedEvent>(lifecycleScope, emitRetained = true){ event ->
            Timber.d("State changed")
            when(event.clientState) {
                ClientState.waitNumber -> {
                    bottomSheetFragment =
                        PhoneNumberFragment()

                    (bottomSheetFragment as PhoneNumberFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as PhoneNumberFragment).tag
                    )

                }
                ClientState.waitCode -> {
                    if (removeRetained<StateChangedEvent>()?.clientState == ClientState.waitCode ||
                        removeRetained<StateChangedEvent>()?.clientState == null){
                            bottomSheetFragment =
                                CodeFragment()


                            (bottomSheetFragment as CodeFragment).show(
                                this.supportFragmentManager,
                                (bottomSheetFragment as CodeFragment).tag
                            )
                    }
                }
                ClientState.waitPassword -> {
                    removeRetained<StateChangedEvent>()
                    if(removeRetained<StateChangedEvent>()?.clientState == ClientState.waitPassword ||
                        removeRetained<StateChangedEvent>()?.clientState == null) {
                        if(bottomSheetFragment != null){
                            bottomSheetFragment?.dismiss()
                        }
                        bottomSheetFragment =
                            PasswordFragment()
                        (bottomSheetFragment as PasswordFragment).show(
                            this.supportFragmentManager,
                            (bottomSheetFragment as PasswordFragment).tag
                        )
                    }
                }
                ClientState.ready -> {
                    //finish()
                }
                else -> {

                }
            }
        }

        subscribe<PhoneChangedEvent>(lifecycleScope){ event ->
            binding.labelNumber.text = event.newNumber
        }
    }

    private fun showLoading(show : Boolean){
        if(show){
            binding.connTgProgress.visibility = View.VISIBLE
            binding.connTgResetTelegramAccount.isEnabled = false
            binding.connTgResetNumber.isEnabled = false
        }
        else{
            binding.connTgProgress.visibility = View.GONE
            binding.connTgResetTelegramAccount.isEnabled = true
            binding.connTgResetNumber.isEnabled = true
        }
    }
}