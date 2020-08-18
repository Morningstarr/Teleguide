package com.mongodb.alliance.ui.telegram

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.ChannelsActivity
import com.mongodb.alliance.R
import com.mongodb.alliance.databinding.ActivityConnectTelegramBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.services.telegram.Service
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.Telegram
import dev.whyoleg.ktd.TelegramClient
import dev.whyoleg.ktd.TelegramClientConfiguration
import dev.whyoleg.ktd.api.TdApi
import dev.whyoleg.ktd.api.TdApi.AuthorizationStateWaitPhoneNumber
import dev.whyoleg.ktd.api.TdApi.AuthorizationStateWaitTdlibParameters
import dev.whyoleg.ktd.api.TelegramObject
import dev.whyoleg.ktd.api.tdlib.setTdlibParameters
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.lang.Runnable
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
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
    private val telegram = Telegram(
        configuration = TelegramClientConfiguration(
            maxEventsCount = 100,
            receiveTimeout = 100.seconds
        )
    )


    //val client = t_service.returnServiceObj() as TelegramClient


    lateinit var bottomSheetFragment : BottomSheetDialogFragment;

    /*override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job*/

    override fun onDestroy() {
        super.onDestroy()
        //coroutineContext.cancelChildren()
        //client.cancel()
    }

    fun onResult(result: TelegramObject) {
        this@ConnectTelegramActivity.runOnUiThread(Runnable {
            Toast.makeText(baseContext, result.toString(), Toast.LENGTH_SHORT)
                .show()
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect_telegram)
        setSupportActionBar(findViewById(R.id.toolbar))
        t_service.initService()
        /*lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                client.updates.onEach { value ->
                    when (value) {
                        is TdApi.UpdateAuthorizationState -> {
                            val state = (value as TdApi.UpdateAuthorizationState).authorizationState
                            when (state) {
                                is AuthorizationStateWaitTdlibParameters -> {
                                    client.setTdlibParameters(
                                        TdApi.TdlibParameters(
                                            apiId = 1682238,
                                            apiHash = "c82da36c7e0b4b4b0bf9a22a6ac5cad0",
                                            databaseDirectory = applicationContext.filesDir.absolutePath,
                                            filesDirectory = applicationContext.filesDir.absolutePath,
                                            applicationVersion = "1.0",
                                            systemLanguageCode = "en",
                                            deviceModel = "Android",
                                            systemVersion = "gtfo"
                                        )
                                    )
                                }
                                is TdApi.AuthorizationStateWaitEncryptionKey -> {
                                    client.exec(TdApi.CheckDatabaseEncryptionKey())
                                }
                                is AuthorizationStateWaitPhoneNumber -> {
                                    Timber.d("Waiting for number");
                                    bottomSheetFragment =
                                        PhoneNumberFragment()
                                    bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
                                }
                                is TdApi.AuthorizationStateWaitCode -> {
                                    Timber.d("Waiting for code");
                                    bottomSheetFragment =
                                        CodeFragment()
                                    bottomSheetFragment.show(
                                        supportFragmentManager,
                                        bottomSheetFragment.tag
                                    )
                                }
                                is TdApi.AuthorizationStateWaitPassword -> {
                                    Timber.d("Waiting for password");
                                    bottomSheetFragment =
                                        PasswordFragment()
                                    bottomSheetFragment.show(
                                        supportFragmentManager,
                                        bottomSheetFragment.tag
                                    )
                                }
                                is TdApi.AuthorizationStateReady -> {
                                    val intent = Intent(baseContext, ChannelsActivity::class.java)
                                    startActivity(intent)
                                }
                            }
                        }
                    }
                }.catch { e ->
                    Timber.e(e.message)
                    Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT)
                        .show()

                }.collect()

            }
        }*/

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            bottomSheetFragment =
                    PhoneNumberFragment()
                bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
        }
    }
}