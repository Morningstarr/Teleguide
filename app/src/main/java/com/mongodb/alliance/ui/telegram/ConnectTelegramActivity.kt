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
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import cafe.adriel.broker.unsubscribe
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.PhoneChangedEvent
import com.mongodb.alliance.R
import com.mongodb.alliance.databinding.ActivityConnectTelegramBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.model.StateChangedEvent
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
        actionbar!!.title = "Connect telegram"

        binding = ActivityConnectTelegramBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        lateinit var behavior: BottomSheetBehavior<View>

        subscribe<StateChangedEvent>(lifecycleScope, emitRetained = true){ event ->
            Timber.d("State changed")
            when(event.clientState){
                ClientState.waitNumber -> {
                    bottomSheetFragment =
                        PhoneNumberFragment()

                    (bottomSheetFragment as PhoneNumberFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as PhoneNumberFragment).tag
                    )

                }
                ClientState.waitCode -> {
                    bottomSheetFragment =
                        CodeFragment()

                    (bottomSheetFragment as CodeFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as CodeFragment).tag
                    )
                }
                ClientState.waitPassword -> {
                    bottomSheetFragment =
                        PasswordFragment()
                    (bottomSheetFragment as PasswordFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as PasswordFragment).tag
                    )
                }
                ClientState.ready -> {
                    //finish()
                }
            }
            //bottomSheetFragment = null
        }

        subscribe<PhoneChangedEvent>(lifecycleScope){ event ->
            binding.labelNumber.text = event.newNumber
        }

        lifecycleScope.launch {
            val task = async {
                withContext(Dispatchers.IO) {
                    (t_service as TelegramService).returnClientState()
                }
            }
            val state = task.await()
            when(state){
                ClientState.ready ->{
                    //Toast.makeText(baseContext, "ready", Toast.LENGTH_SHORT).show()
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
            if(bottomSheetFragment != null) {
                bottomSheetFragment!!.show(supportFragmentManager, bottomSheetFragment!!.tag)
            }
            else{
                Toast.makeText(baseContext, "Nothing to show", Toast.LENGTH_SHORT).show()
            }
        }

        binding.connTgResetNumber.setOnClickListener { view ->
            lifecycleScope.launch {
                val task = async {
                    (t_service as TelegramService).getPhoneNumber()
                }
                val number = task.await()

                if(number == ""){
                    Toast.makeText(baseContext, "No number is connected", Toast.LENGTH_SHORT).show()
                }
                else{
                    //(t_service as TelegramService).resetPhoneNumber()
                    Toast.makeText(baseContext, "You can't reset number while your account is connected. Please reconnect your telegram", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.connTgResetTelegramAccount.setOnClickListener { view ->
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Alert")
            builder.setMessage("Are you sure you want change Telegram Account?")

            builder.setPositiveButton(android.R.string.yes) { dialog, which ->
                lifecycleScope.launch {
                    (t_service as TelegramService).logOut()
                    (t_service as TelegramService).resetPhoneNumber()
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
                //todo окно подтверждения пропуска привязки
                finish()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        unsubscribe()
    }

}