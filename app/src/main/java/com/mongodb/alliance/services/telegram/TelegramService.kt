package com.mongodb.alliance.services.telegram

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.ChannelProj
import com.mongodb.alliance.ChannelsActivity
import com.mongodb.alliance.ui.telegram.CodeFragment
import com.mongodb.alliance.ui.telegram.PasswordFragment
import com.mongodb.alliance.ui.telegram.PhoneNumberFragment
import dev.whyoleg.ktd.Telegram
import dev.whyoleg.ktd.TelegramClient
import dev.whyoleg.ktd.TelegramClientConfiguration
import dev.whyoleg.ktd.api.TdApi
import dev.whyoleg.ktd.api.TelegramObject
import dev.whyoleg.ktd.api.tdlib.setTdlibParameters
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
@InternalCoroutinesApi
@Singleton
class TelegramService : Service {

    private var telegram : Telegram
    private var client : TelegramClient

    @Inject constructor() {
        telegram = Telegram(
            configuration = TelegramClientConfiguration(
                maxEventsCount = 100,
                receiveTimeout = 100.seconds
            )
        )
        client = telegram.client()
    }

    lateinit var bottomSheetFragment : BottomSheetDialogFragment;

    override suspend fun initService(context : AppCompatActivity) {
        //lateinit var clientState : ClientState
        client.updates.onEach { value ->
            when (value) {
                is TdApi.UpdateAuthorizationState -> {
                    val state = value.authorizationState
                    when (state) {
                        is TdApi.AuthorizationStateWaitTdlibParameters -> {
                            client.setTdlibParameters(
                                TdApi.TdlibParameters(
                                    apiId = 1682238,
                                    apiHash = "c82da36c7e0b4b4b0bf9a22a6ac5cad0",
                                    databaseDirectory = ChannelProj.getContxt().filesDir.absolutePath,
                                    filesDirectory = ChannelProj.getContxt().filesDir.absolutePath,
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
                        is TdApi.AuthorizationStateWaitPhoneNumber -> {
                            Timber.d("Waiting for number");
                            //clientState = ClientState.waitNumber
                            bottomSheetFragment =
                                PhoneNumberFragment()
                            bottomSheetFragment.show(
                                context.supportFragmentManager,
                                bottomSheetFragment.tag
                            )
                        }
                        is TdApi.AuthorizationStateWaitCode -> {
                            Timber.d("Waiting for code");
                            //clientState = ClientState.waitCode
                            bottomSheetFragment =
                                CodeFragment()
                            bottomSheetFragment.show(
                                context.supportFragmentManager,
                                bottomSheetFragment.tag
                            )
                        }
                        is TdApi.AuthorizationStateWaitPassword -> {
                            Timber.d("Waiting for password");
                            //clientState = ClientState.waitPassword
                            bottomSheetFragment =
                                PasswordFragment()
                            bottomSheetFragment.show(
                                context.supportFragmentManager,
                                bottomSheetFragment.tag
                            )
                        }
                        is TdApi.AuthorizationStateReady -> {
                            Timber.d("State ready");
                            //clientState = ClientState.ready
                            val intent = Intent(context.baseContext, ChannelsActivity::class.java)
                            startActivity(context.baseContext, intent, null)
                            //startActivity(Intent(context, LoginActivity::class.java))
                        }
                    }
                }

            }

        }.catch { e ->
            Timber.e(e.message)
            /*Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT)
                .show()*/

        }.collect()
    }

    suspend fun callCodeConfirm(input : String): TelegramObject {
        return client.exec(TdApi.CheckAuthenticationCode(input))
    }

    override fun returnServiceObj(): TelegramClient {
        return client
    }

    fun isInit() : Boolean {
        if (client == null){
            return false
        }
        else{
            return true
        }
    }
    /*suspend fun initializeClient(context : Activity){
        //lifecycleScope.launch {
            //withContext(Dispatchers.IO) {
                client.updates.onEach { value ->
                    when (value) {
                        is TdApi.UpdateAuthorizationState -> {
                            val state = (value as TdApi.UpdateAuthorizationState).authorizationState
                            when (state) {
                                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                                    client.setTdlibParameters(
                                        TdApi.TdlibParameters(
                                            apiId = 1682238,
                                            apiHash = "c82da36c7e0b4b4b0bf9a22a6ac5cad0",
                                            databaseDirectory = context.applicationContext.filesDir.absolutePath,
                                            filesDirectory = context.applicationContext.filesDir.absolutePath,
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
                                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                                    Timber.d("Waiting for number");
                                    bottomSheetFragment =
                                        PhoneNumberFragment()
                                    bottomSheetFragment.show(context.supportFragmentManager, bottomSheetFragment.tag)
                                }
                                is TdApi.AuthorizationStateWaitCode -> {
                                    Timber.d("Waiting for code");
                                    bottomSheetFragment =
                                        CodeFragment()
                                    bottomSheetFragment.show(
                                        context.supportFragmentManager,
                                        bottomSheetFragment.tag
                                    )
                                }
                                is TdApi.AuthorizationStateWaitPassword -> {
                                    Timber.d("Waiting for password");
                                    bottomSheetFragment =
                                        PasswordFragment()
                                    bottomSheetFragment.show(
                                        context.supportFragmentManager,
                                        bottomSheetFragment.tag
                                    )
                                }
                                is TdApi.AuthorizationStateReady -> {
                                    val intent = Intent(context.baseContext, ChannelsActivity::class.java)
                                    startActivity(intent)
                                }
                            }
                        }
                    }
                }.catch { e ->
                    Timber.e(e.message)
                    /*Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT)
                        .show()*/

                }.collect()

            //}
        //}
    }*/
}