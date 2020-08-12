package com.mongodb.alliance

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.mongodb.alliance.model.OrderedChat
import org.drinkless.td.libcore.telegram.Client
import org.drinkless.td.libcore.telegram.TdApi
import org.drinkless.td.libcore.telegram.TdApi.*
import java.io.IOError
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


class TdLibTestActivity : AppCompatActivity() {

    private var client: Client? = null

    private val authorizationState: AuthorizationState? = null

    private var haveAuthorization = false

    private val quiting = false

    /*private val defaultHandler: Client.ResultHandler =
        DefaultHandler()*/

    private val authorizationLock: Lock = ReentrantLock()
    private val gotAuthorization: Condition = authorizationLock.newCondition()

    private val users: ConcurrentMap<Int, User> = ConcurrentHashMap<Int, User>()
    private val basicGroups: ConcurrentMap<Int, BasicGroup> = ConcurrentHashMap<Int, BasicGroup>()
    private val supergroups: ConcurrentMap<Int, Supergroup> = ConcurrentHashMap<Int, Supergroup>()
    private val secretChats: ConcurrentMap<Int, SecretChat> = ConcurrentHashMap<Int, SecretChat>()

    private val chats: ConcurrentMap<Long, Chat> = ConcurrentHashMap<Long, Chat>()
    private val mainChatList: NavigableSet<OrderedChat> = TreeSet<OrderedChat>()
    private var haveFullMainChatList = false

    private val usersFullInfo: ConcurrentMap<Int, UserFullInfo> =
        ConcurrentHashMap<Int, UserFullInfo>()
    private val basicGroupsFullInfo: ConcurrentMap<Int, BasicGroupFullInfo> =
        ConcurrentHashMap<Int, BasicGroupFullInfo>()
    private val supergroupsFullInfo: ConcurrentMap<Int, SupergroupFullInfo> =
        ConcurrentHashMap<Int, SupergroupFullInfo>()

    private val newLine = System.getProperty("line.separator")
    private val commandsLine =
        "Enter command (gcs - GetChats, gc <chatId> - GetChat, me - GetMe, sm <chatId> <message> - SendMessage, lo - LogOut, q - Quit): "
    private val currentPrompt: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_td_lib_test)

        //var client = Client()
        Client.execute(SetLogVerbosityLevel(0))
        if (Client.execute(
                SetLogStream(
                    LogStreamFile(
                        "tdlib.log",
                        1 shl 27
                    )
                )
            ) is Error
        ) {
            throw IOError(IOException("Write access to the current directory is required"))
        }

        // create client

        // create client
        //client = Client.create(UpdatesHandler(), null, null)

        // test Client.execute

        // test Client.execute
        //defaultHandler.onResult(Client.execute(GetTextEntities("@telegram /test_command https://telegram.org telegram.me @gif @test")))

        // main loop
        while (!quiting) {
            // await authorization
            authorizationLock.lock()
            try {
                while (!haveAuthorization) {
                    gotAuthorization.await()
                }
            } finally {
                authorizationLock.unlock()
            }
            while (haveAuthorization) {
                getMainChatList(20)
            }
        }


    }

    public fun onAuthorizationStateUpdated(authorizationState: AuthorizationState?) {
        /*if (authorizationState != null) {
            Example.authorizationState = authorizationState
        }
        when (Example.authorizationState.getConstructor()) {
            AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                val parameters = TdlibParameters()
                parameters.databaseDirectory = "tdlib"
                parameters.useMessageDatabase = true
                parameters.useSecretChats = true
                parameters.apiId = 94575
                parameters.apiHash = "a3406de8d171bb422bb6ddf3bbd800e2"
                parameters.systemLanguageCode = "en"
                parameters.deviceModel = "Desktop"
                parameters.applicationVersion = "1.0"
                parameters.enableStorageOptimizer = true
                client!!.send(SetTdlibParameters(parameters), AuthorizationRequestHandler())
            }
            AuthorizationStateWaitEncryptionKey.CONSTRUCTOR -> client!!.send(
                CheckDatabaseEncryptionKey(),
                AuthorizationRequestHandler()
            )
            AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                val phoneNumber: String = promptString("Please enter phone number: ")
                client!!.send(
                    SetAuthenticationPhoneNumber(phoneNumber, null),
                    AuthorizationRequestHandler()
                )
            }
            AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR -> {
                val link =
                    (Example.authorizationState as AuthorizationStateWaitOtherDeviceConfirmation).link
                println("Please confirm this login link on another device: $link")
            }
            AuthorizationStateWaitCode.CONSTRUCTOR -> {
                val code: String = promptString("Please enter authentication code: ")
                client!!.send(CheckAuthenticationCode(code), AuthorizationRequestHandler())
            }
            AuthorizationStateWaitRegistration.CONSTRUCTOR -> {
                val firstName: String = promptString("Please enter your first name: ")
                val lastName: String = promptString("Please enter your last name: ")
                client!!.send(RegisterUser(firstName, lastName), AuthorizationRequestHandler())
            }
            AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                val password: String = promptString("Please enter password: ")
                client!!.send(CheckAuthenticationPassword(password), AuthorizationRequestHandler())
            }
            AuthorizationStateReady.CONSTRUCTOR -> {
                haveAuthorization = true
                authorizationLock.lock()
                try {
                    gotAuthorization.signal()
                } finally {
                    authorizationLock.unlock()
                }
            }
            AuthorizationStateLoggingOut.CONSTRUCTOR -> {
                haveAuthorization = false
                print("Logging out")
            }
            AuthorizationStateClosing.CONSTRUCTOR -> {
                haveAuthorization = false
                print("Closing")
            }
            AuthorizationStateClosed.CONSTRUCTOR -> {
                print("Closed")
                if (!quiting) {
                    client = Client.create(
                        UpdatesHandler(),
                        null,
                        null
                    ) // recreate client after previous has closed
                }
            }
            else -> System.err.println("Unsupported authorization state:" + newLine + Example.authorizationState)
        }*/
    }

    private fun getMainChatList(limit: Int) {
        synchronized(mainChatList) {
            if (!haveFullMainChatList && limit > mainChatList.size) {
                // have enough chats in the chat list or chat list is too small
                var offsetOrder = Long.MAX_VALUE
                var offsetChatId: Long = 0
                if (!mainChatList.isEmpty()) {
                    val last: OrderedChat = mainChatList.last()
                    offsetOrder = last.position.toLong()
                    offsetChatId = last.chatId
                }
                client!!.send(
                    GetChats(
                        ChatListMain(),
                        offsetOrder,
                        offsetChatId,
                        limit - mainChatList.size
                    )
                ) { `object` ->
                    when (`object`.constructor) {
                        Error.CONSTRUCTOR -> Log.v(TAG(), "Receive an error for GetChats:$newLine$`object`")
                        Chats.CONSTRUCTOR -> {
                            val chatIds = (`object` as Chats).chatIds
                            if (chatIds.size == 0) {
                                synchronized(mainChatList) { haveFullMainChatList = true }
                            }
                            // chats had already been received through updates, let's retry request
                            getMainChatList(limit)
                        }
                        else -> Log.v(TAG(), "Receive wrong response from TDLib:$newLine$`object`")
                    }
                }
                return
            }

            //todo перечисление чатов (вывести)
            val iter: Iterator<OrderedChat> = mainChatList.iterator()
            //println()
            //println("First " + limit + " chat(s) out of " + mainChatList.size + " known chat(s):")
            for (i in 0 until limit) {
                val chatId: Long = iter.next().chatId
                val chat = chats[chatId]
                //synchronized(chat!!) { println(chatId.toString() + ": " + chat.title) }
            }
            //print("")
        }
    }

/*public class UpdatesHandler :
    Client.ResultHandler {
    override fun onResult(`object`: Object) {
        when (`object`.constructor) {
            UpdateAuthorizationState.CONSTRUCTOR -> onAuthorizationStateUpdated((`object` as UpdateAuthorizationState).authorizationState)
            UpdateUser.CONSTRUCTOR -> {
                val updateUser = `object` as UpdateUser
                users.put(updateUser.user.id, updateUser.user)
            }
            UpdateUserStatus.CONSTRUCTOR -> {
                val updateUserStatus = `object` as UpdateUserStatus
                val user: User = users.get(updateUserStatus.userId)
                synchronized(user) { user.status = updateUserStatus.status }
            }
            UpdateBasicGroup.CONSTRUCTOR -> {
                val updateBasicGroup = `object` as UpdateBasicGroup
                basicGroups.put(updateBasicGroup.basicGroup.id, updateBasicGroup.basicGroup)
            }
            UpdateSupergroup.CONSTRUCTOR -> {
                val updateSupergroup = `object` as UpdateSupergroup
                supergroups.put(updateSupergroup.supergroup.id, updateSupergroup.supergroup)
            }
            UpdateSecretChat.CONSTRUCTOR -> {
                val updateSecretChat = `object` as UpdateSecretChat
                secretChats.put(updateSecretChat.secretChat.id, updateSecretChat.secretChat)
            }
            UpdateNewChat.CONSTRUCTOR -> {
                val updateNewChat = `object` as UpdateNewChat
                val chat = updateNewChat.chat
                synchronized(chat) {
                    chats.put(chat.id, chat)
                    val positions: Array<TdApi.ChatPosition> = chat.positions
                    chat.positions = arrayOfNulls<TdApi.ChatPosition>(0)
                    setChatPositions(chat, positions)
                }
            }
            UpdateChatTitle.CONSTRUCTOR -> {
                val updateChat = `object` as UpdateChatTitle
                val chat: Chat = chats.get(updateChat.chatId)
                synchronized(chat) { chat.title = updateChat.title }
            }
            UpdateChatPhoto.CONSTRUCTOR -> {
                val updateChat = `object` as UpdateChatPhoto
                val chat: Chat = chats.get(updateChat.chatId)
                synchronized(chat) { chat.photo = updateChat.photo }
            }
            UpdateChatLastMessage.CONSTRUCTOR -> {
                val updateChat = `object` as UpdateChatLastMessage
                val chat: Chat = chats.get(updateChat.chatId)
                synchronized(chat) {
                    chat.lastMessage = updateChat.lastMessage
                    setChatPositions(chat, updateChat.positions)
                }
            }
            TdApi.UpdateChatPosition.CONSTRUCTOR -> {
                val updateChat: TdApi.UpdateChatPosition = `object` as TdApi.UpdateChatPosition
                if (updateChat.position.list.getConstructor() !== ChatListMain.CONSTRUCTOR) {
                    break
                }
                val chat: Chat = chats.get(updateChat.chatId)
                synchronized(chat) {
                    var i: Int
                    i = 0
                    while (i < chat.positions.length) {
                        if (chat.positions.get(i).list.getConstructor() === ChatListMain.CONSTRUCTOR) {
                            break
                        }
                        i++
                    }
                    val new_positions: Array<TdApi.ChatPosition?> =
                        arrayOfNulls<TdApi.ChatPosition>(chat.positions.length + (if (updateChat.position.order === 0) 0 else 1) - if (i < chat.positions.length) 1 else 0)
                    var pos = 0
                    if (updateChat.position.order !== 0) {
                        new_positions[pos++] = updateChat.position
                    }
                    var j = 0
                    while (j < chat.positions.length) {
                        if (j != i) {
                            new_positions[pos++] = chat.positions.get(j)
                        }
                        j++
                    }
                    assert(pos == new_positions.size)
                    setChatPositions(chat, new_positions)
                }
            }
            UpdateChatReadInbox.CONSTRUCTOR -> {
                val updateChat = `object` as UpdateChatReadInbox
                val chat: Chat = chats.get(updateChat.chatId)
                synchronized(chat) {
                    chat.lastReadInboxMessageId = updateChat.lastReadInboxMessageId
                    chat.unreadCount = updateChat.unreadCount
                }
            }
            UpdateChatReadOutbox.CONSTRUCTOR -> {
                val updateChat = `object` as UpdateChatReadOutbox
                val chat: Chat = chats.get(updateChat.chatId)
                synchronized(
                    chat
                ) { chat.lastReadOutboxMessageId = updateChat.lastReadOutboxMessageId }
            }
            UpdateChatUnreadMentionCount.CONSTRUCTOR -> {
                val updateChat =
                    `object` as UpdateChatUnreadMentionCount
                val chat: Chat = chats.get(updateChat.chatId)
                synchronized(chat) { chat.unreadMentionCount = updateChat.unreadMentionCount }
            }
            UpdateMessageMentionRead.CONSTRUCTOR -> {
                val updateChat = `object` as UpdateMessageMentionRead
                val chat: Chat = chats.get(updateChat.chatId)
                synchronized(chat) { chat.unreadMentionCount = updateChat.unreadMentionCount }
            }
            UpdateChatReplyMarkup.CONSTRUCTOR -> {
                val updateChat = `object` as UpdateChatReplyMarkup
                val chat: Chat = chats.get(updateChat.chatId)
                synchronized(
                    chat
                ) { chat.replyMarkupMessageId = updateChat.replyMarkupMessageId }
            }
            UpdateChatDraftMessage.CONSTRUCTOR -> {
                val updateChat = `object` as UpdateChatDraftMessage
                val chat: Chat = chats.get(updateChat.chatId)
                synchronized(chat) {
                    chat.draftMessage = updateChat.draftMessage
                    setChatPositions(chat, updateChat.positions)
                }
            }
            UpdateChatPermissions.CONSTRUCTOR -> {
                val update = `object` as UpdateChatPermissions
                val chat: Chat = chats.get(update.chatId)
                synchronized(chat) { chat.permissions = update.permissions }
            }
            UpdateChatNotificationSettings.CONSTRUCTOR -> {
                val update =
                    `object` as UpdateChatNotificationSettings
                val chat: Chat = chats.get(update.chatId)
                synchronized(chat) { chat.notificationSettings = update.notificationSettings }
            }
            UpdateChatDefaultDisableNotification.CONSTRUCTOR -> {
                val update =
                    `object` as UpdateChatDefaultDisableNotification
                val chat: Chat = chats.get(update.chatId)
                synchronized(
                    chat
                ) { chat.defaultDisableNotification = update.defaultDisableNotification }
            }
            UpdateChatIsMarkedAsUnread.CONSTRUCTOR -> {
                val update = `object` as UpdateChatIsMarkedAsUnread
                val chat: Chat = chats.get(update.chatId)
                synchronized(chat) { chat.isMarkedAsUnread = update.isMarkedAsUnread }
            }
            UpdateChatHasScheduledMessages.CONSTRUCTOR -> {
                val update =
                    `object` as UpdateChatHasScheduledMessages
                val chat: Chat = chats.get(update.chatId)
                synchronized(chat) { chat.hasScheduledMessages = update.hasScheduledMessages }
            }
            UpdateUserFullInfo.CONSTRUCTOR -> {
                val updateUserFullInfo = `object` as UpdateUserFullInfo
                usersFullInfo.put(updateUserFullInfo.userId, updateUserFullInfo.userFullInfo)
            }
            UpdateBasicGroupFullInfo.CONSTRUCTOR -> {
                val updateBasicGroupFullInfo =
                    `object` as UpdateBasicGroupFullInfo
                basicGroupsFullInfo.put(
                    updateBasicGroupFullInfo.basicGroupId,
                    updateBasicGroupFullInfo.basicGroupFullInfo
                )
            }
            UpdateSupergroupFullInfo.CONSTRUCTOR -> {
                val updateSupergroupFullInfo =
                    `object` as UpdateSupergroupFullInfo
                supergroupsFullInfo.put(
                    updateSupergroupFullInfo.supergroupId,
                    updateSupergroupFullInfo.supergroupFullInfo
                )
            }
            else -> {
            }
        }
    }
}*/

class DefaultHandler :
    Client.ResultHandler {
    override fun onResult(`object`: Object) {
        Log.v(TAG(), `object`.toString())
    }
}

class AuthorizationRequestHandler :
    Client.ResultHandler {
    override fun onResult(`object`: Object) {
        val newLine = System.getProperty("line.separator")
        when (`object`.constructor) {
            Error.CONSTRUCTOR -> {
                //Log.v(TAG(), "Receive an error:$newLine$`object`")
                //onAuthorizationStateUpdated(null) // repeat last action
            }
            Ok.CONSTRUCTOR -> {
            }
            //else -> Log.v(TAG(), "Receive wrong response from TDLib:$newLine$`object`")
        }
    }
}
}




