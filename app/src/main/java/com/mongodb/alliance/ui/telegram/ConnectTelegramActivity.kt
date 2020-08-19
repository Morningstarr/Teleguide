package com.mongodb.alliance.ui.telegram

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.R
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.services.telegram.Service
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.Telegram
import dev.whyoleg.ktd.TelegramClientConfiguration
import dev.whyoleg.ktd.api.TelegramObject
import kotlinx.coroutines.*
import java.lang.Runnable
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class ConnectTelegramActivity : AppCompatActivity()/*, CoroutineScope*/ {

    @TelegramServ
    @Inject lateinit var t_service: Service
    private lateinit var code: EditText

    //private var job: Job = Job()
    //val client = t_service.returnServiceObj() as TelegramClient
    /*override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job*/

    lateinit var bottomSheetFragment : BottomSheetDialogFragment;

    override fun onDestroy() {
        super.onDestroy()
        //coroutineContext.cancelChildren()
        //client.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect_telegram)
        setSupportActionBar(findViewById(R.id.toolbar))

        lifecycleScope.launch{
            withContext(Dispatchers.IO) {
                t_service.initService(this@ConnectTelegramActivity)
            }
        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            bottomSheetFragment =
                    PhoneNumberFragment()
                bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
        }
    }
}