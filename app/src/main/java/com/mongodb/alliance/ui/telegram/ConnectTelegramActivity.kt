package com.mongodb.alliance.ui.telegram

import android.os.Bundle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import com.mongodb.alliance.R
import dev.whyoleg.ktd.Telegram
import dev.whyoleg.ktd.TelegramClient
import dev.whyoleg.ktd.TelegramClientConfiguration
import dev.whyoleg.ktd.api.TdApi
import dev.whyoleg.ktd.api.TdApi.AuthorizationStateWaitPhoneNumber
import dev.whyoleg.ktd.api.TdApi.AuthorizationStateWaitTdlibParameters
import dev.whyoleg.ktd.api.TelegramUpdate
import dev.whyoleg.ktd.api.TelegramObject
import dev.whyoleg.ktd.api.tdlib.setTdlibParameters
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@InternalCoroutinesApi
@ExperimentalTime
class ConnectTelegramActivity : AppCompatActivity(), CoroutineScope {

    private var job: Job = Job()
    private val telegram = Telegram(
        configuration = TelegramClientConfiguration(
            maxEventsCount = 100,
            receiveTimeout = 100.seconds
        )
    )

    private val client = telegram.client()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        client.cancel()
    }

    suspend fun callGetApi(): TelegramObject {
        return client.exec(TdApi.SetAuthenticationPhoneNumber("+79177777210",null))
    }

    fun onResult(result: TelegramObject) {
        print(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect_telegram)
        setSupportActionBar(findViewById(R.id.toolbar))

        launch {
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
                                val result = callGetApi()
                                onResult(result)
                            }
                            is TdApi.AuthorizationStateWaitCode -> {
                                println("Waiting for code")
                            }
                        }
                    }
                }
            }.catch { e ->
                println(e)
            }.collect()


        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
    }
}