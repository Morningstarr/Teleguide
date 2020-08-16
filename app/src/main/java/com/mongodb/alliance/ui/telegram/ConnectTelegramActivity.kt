package com.mongodb.alliance.ui.telegram

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.R
import com.mongodb.alliance.databinding.ActivityConnectTelegramBinding
import dev.whyoleg.ktd.Telegram
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
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@InternalCoroutinesApi
@ExperimentalTime
class ConnectTelegramActivity : AppCompatActivity()/*, CoroutineScope*/ {



    inline fun <reified T> T.TAG(): String = T::class.java.simpleName
    private lateinit var code: EditText
    //private var job: Job = Job()
    private val telegram = Telegram(
        configuration = TelegramClientConfiguration(
            maxEventsCount = 100,
            receiveTimeout = 100.seconds
        )
    )

    val client = telegram.client()
    lateinit var bottomSheetFragment : BaseBottomSheetFragment;

    /*override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job*/

    override fun onDestroy() {
        super.onDestroy()
        //coroutineContext.cancelChildren()
        client.cancel()
    }

    suspend fun callGetApi(): TelegramObject {
        return client.exec(TdApi.SetAuthenticationPhoneNumber("+380713312170",null))
    }

    suspend fun callGetApi(number : String): TelegramObject {
        return client.exec(TdApi.SetAuthenticationPhoneNumber(number,null))
    }

    suspend fun callCode(): TelegramObject {
        return client.exec(TdApi.CheckAuthenticationCode(code.text.toString()))
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

        lifecycleScope.launch {
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
                                is TdApi.AuthorizationStateWaitEncryptionKey ->
                                {
                                    client.exec(TdApi.CheckDatabaseEncryptionKey())
                                }
                                is AuthorizationStateWaitPhoneNumber ->
                                {
                                    Timber.d("Waiting for number");
                                    bottomSheetFragment =
                                        PhoneNumberFragment()
                                    bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
                                }
                                is TdApi.AuthorizationStateWaitCode ->
                                {
                                    Timber.d("Waiting for code");
                                    bottomSheetFragment =
                                        CodeFragment()
                                    bottomSheetFragment.show(
                                        supportFragmentManager,
                                        bottomSheetFragment.tag
                                    )
                                }
                                is TdApi.AuthorizationStateWaitPassword ->
                                {
                                    Timber.d("Waiting for password");
                                    bottomSheetFragment =
                                        PasswordFragment()
                                    bottomSheetFragment.show(
                                        supportFragmentManager,
                                        bottomSheetFragment.tag
                                    )
                                }
                            }
                        }
                    }
                }.catch { e ->
                    Timber.e(e.message);
                    Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT)
                        .show()

                }.collect()

            }
        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            /*val bottomSheetFragment = BottomSheetFragment(1)
            bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
            launch {
                val result = callCode()
                onResult(result)
            }*/
        }
    }
}