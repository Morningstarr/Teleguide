package com.mongodb.alliance.ui.telegram

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import cafe.adriel.broker.unsubscribe
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.ChannelsActivity
import com.mongodb.alliance.R
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.model.StateChangedEvent
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.Telegram
import dev.whyoleg.ktd.TelegramClientConfiguration
import dev.whyoleg.ktd.api.TelegramObject
import kotlinx.coroutines.*
import timber.log.Timber
import java.lang.Runnable
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class ConnectTelegramActivity : AppCompatActivity(), GlobalBroker.Subscriber {

    @TelegramServ
    @Inject lateinit var t_service: Service
    private lateinit var code: EditText
    lateinit var bottomSheetFragment : BottomSheetDialogFragment;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect_telegram)
        setSupportActionBar(findViewById(R.id.toolbar))

        subscribe<StateChangedEvent>(lifecycleScope){ event ->
            Timber.d("State changed")
            when(event.clientState){
                ClientState.waitNumber -> {
                    bottomSheetFragment =
                        PhoneNumberFragment()
                    bottomSheetFragment.show(
                        this.supportFragmentManager,
                        bottomSheetFragment.tag
                    )
                }
                ClientState.waitCode -> {
                    bottomSheetFragment =
                        CodeFragment()

                    bottomSheetFragment.show(
                        this.supportFragmentManager,
                        bottomSheetFragment.tag
                    )
                }
                ClientState.waitPassword -> {
                    bottomSheetFragment =
                        PasswordFragment()
                    bottomSheetFragment.show(
                        this.supportFragmentManager,
                        bottomSheetFragment.tag
                    )
                }
                ClientState.ready -> {
                    finish()
                }
            }
        }

        lifecycleScope.launch{
            withContext(Dispatchers.IO) {
                t_service.initService()
            }
        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            bottomSheetFragment =
                    PhoneNumberFragment()
                bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
        }
    }

    override fun onStop() {
        super.onStop()
        unsubscribe()
    }
}