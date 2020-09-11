package com.mongodb.alliance.services.telegram

import android.os.Build
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.getRetained
import cafe.adriel.broker.publish
import com.mongodb.alliance.ChannelProj
import com.mongodb.alliance.events.RegistrationCompletedEvent
import com.mongodb.alliance.events.StateChangedEvent
import dev.whyoleg.ktd.Telegram
import dev.whyoleg.ktd.TelegramClient
import dev.whyoleg.ktd.TelegramClientConfiguration
import dev.whyoleg.ktd.api.TdApi
import dev.whyoleg.ktd.api.TelegramObject
import dev.whyoleg.ktd.api.authorization.getAuthorizationState
import dev.whyoleg.ktd.api.phone.sharePhoneNumber
import dev.whyoleg.ktd.api.tdlib.setTdlibParameters
import dev.whyoleg.ktd.api.user.getMe
import dev.whyoleg.ktd.api.util.close
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
@InternalCoroutinesApi
@Singleton
class TelegramService : Service, GlobalBroker.Publisher, GlobalBroker.Subscriber {

    private var telegram : Telegram
    private var client : TelegramClient
    private lateinit var clientState : ClientState

    @Inject constructor() {
        telegram = Telegram(
            configuration = TelegramClientConfiguration(
                maxEventsCount = 100,
                receiveTimeout = 100.seconds
            )
        )
        client = telegram.client()
    }


    suspend fun returnClientState(): ClientState{
        try {
            when (client.getAuthorizationState()) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    return ClientState.waitParameters
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    clientState = ClientState.waitNumber
                    /*publish(
                        StateChangedEvent(
                            clientState
                        )/*, retain = true*/)*/
                    return ClientState.waitNumber
                }
                is TdApi.AuthorizationStateWaitCode -> {
                    return ClientState.waitCode
                }
                is TdApi.AuthorizationStateWaitPassword -> {
                    return ClientState.waitPassword
                }
                is TdApi.AuthorizationStateReady -> {
                    return ClientState.ready
                }
                is TdApi.AuthorizationStateLoggingOut -> {
                    return ClientState.loggingOut
                }
                is TdApi.AuthorizationStateClosed -> {
                    var newClient = telegram.client()
                    client = newClient
                    setUpClient()
                    resetPhoneNumber()
                    //publish(ClientState.waitNumber)
                    return ClientState.waitNumber
                }
                else -> {
                    return ClientState.undefined
                }
            }
        }
        catch(e:Exception){
            Timber.e(e.message)
            return ClientState.undefined
        }
    }

    override suspend fun setUpClient(){
        try {
            val clientState = ClientState.setParameters
            if (client.getAuthorizationState()
                    .toString() == TdApi.AuthorizationStateWaitTdlibParameters().toString()
            ) {

                client.setTdlibParameters(
                    TdApi.TdlibParameters(
                        apiId = 1682238,
                        apiHash = "c82da36c7e0b4b4b0bf9a22a6ac5cad0",
                        databaseDirectory = ChannelProj.getContxt().filesDir.absolutePath,
                        filesDirectory = ChannelProj.getContxt().filesDir.absolutePath,
                        applicationVersion = "1.0",
                        systemLanguageCode = "en",
                        deviceModel = Build.MODEL,
                        systemVersion = Build.VERSION.SDK_INT.toString()
                    )
                )
                client.exec(TdApi.CheckDatabaseEncryptionKey())
            }
            publish(StateChangedEvent(clientState))
        }
        catch(e: Exception){
            Timber.e(e.message)
        }
    }

    @ExperimentalCoroutinesApi
    override suspend fun initService() {
        client.updates.onEach { value ->
            when (value) {
                is TdApi.UpdateAuthorizationState -> {
                    val state = value.authorizationState
                    when (state) {
                        is TdApi.AuthorizationStateWaitPhoneNumber -> {
                            Timber.d("Waiting for number");
                            if (getRetained<StateChangedEvent>() == null /*&& returnClientState() != ClientState.ready*//*||*/
                                ) {
                                clientState = ClientState.waitNumber
                                publish(StateChangedEvent(clientState), retain = true)
                            }
                        }
                        is TdApi.AuthorizationStateWaitCode -> {
                            Timber.d("Waiting for code");
                            if ((getRetained<StateChangedEvent>() == null && returnClientState() != ClientState.ready) ||
                                getRetained<StateChangedEvent>()?.clientState == ClientState.waitNumber) {
                                clientState = ClientState.waitCode
                                publish(StateChangedEvent(clientState), retain = true)
                            }
                        }
                        is TdApi.AuthorizationStateWaitPassword -> {
                            Timber.d("Waiting for password");
                            clientState = ClientState.waitPassword
                            if ((getRetained<StateChangedEvent>() == null && returnClientState() != ClientState.ready) ||
                                getRetained<StateChangedEvent>()?.clientState == ClientState.waitCode) {
                                publish(StateChangedEvent(clientState), retain = true)
                            }
                        }
                        is TdApi.AuthorizationStateReady -> {
                            Timber.d("State ready");
                            clientState = ClientState.completed
                            publish(RegistrationCompletedEvent(clientState), retain = true)
                        }
                    }
                }
            }

        }.catch {
            Timber.e(it.message)
        }
        .collect()
    }

    suspend fun callCodeConfirm(input : String): TelegramObject {
        return client.exec(TdApi.CheckAuthenticationCode(input))
    }

    suspend fun callPasswordConfirm(input : String): TelegramObject {
        return client.exec(TdApi.CheckAuthenticationPassword(input))
    }

    suspend fun callNumberConfirm(input : String): TelegramObject {
        return client.exec(TdApi.SetAuthenticationPhoneNumber(input,null))
    }

    suspend fun getChatIds() : LongArray {
        val getChats = TdApi.GetChats(TdApi.ChatListMain(), Long.MAX_VALUE, 0, Int.MAX_VALUE)   //TODO возмножно стоить изменить max_value на что-то другое
        val chats : TdApi.Chats = client.exec(getChats) as TdApi.Chats
        return chats.chatIds
    }

    suspend fun getChats(): List<TdApi.Chat> = getChatIds()
        .map { ids -> getChat(ids) }

    suspend fun getChat(chatId: Long): TdApi.Chat {
        return client.exec(TdApi.GetChat(chatId)) as TdApi.Chat
    }


    override fun returnServiceObj(): TelegramClient {
        return client
    }

    suspend fun logOut(){
        try{
            client.exec(TdApi.LogOut())
        }
        catch(e: Exception){
            Timber.e(e.message)
        }
    }

    suspend fun clientClose(){
        try{
            client.close()
        }
        catch(e:Exception){
            Timber.e(e.message)
        }
    }

    suspend fun returnSupergroup(id : Int) : String{
        return (client.exec(TdApi.GetSupergroup(id)) as TdApi.Supergroup).username
    }

    suspend fun returnChat(id : Long) {
        var chat = client.exec(TdApi.GetChat(id)) as TdApi.Chat

    }

    suspend fun returnGroupChat(id : Int) {
        var group_chat = client.exec(TdApi.GetBasicGroup(id)) as TdApi.BasicGroup
    }

    suspend fun getPhoneNumber(): String {
        lateinit var number : String
        return try {
            number = client.getMe().phoneNumber
            number
        } catch (e:Exception){
            ""
        }
    }

    suspend fun getProfileName(): String{
        lateinit var username : String
        return try{
            username = client.getMe().username
            username
        } catch (e:Exception){
            ""
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun resetPhoneNumber(){
        TdApi.SetAuthenticationPhoneNumber()
        returnClientState()
        val clientState = ClientState.waitNumber
        publish(StateChangedEvent(clientState), retain = true)
    }

    @ExperimentalCoroutinesApi
    suspend fun changeAccount(){
        var newClient = telegram.client()
        client = newClient
        setUpClient()
        TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber())
        val clientState = ClientState.waitNumber
        publish(StateChangedEvent(clientState), retain = true)
        initService()
    }

}