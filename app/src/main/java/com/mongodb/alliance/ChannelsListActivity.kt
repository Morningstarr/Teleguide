package com.mongodb.alliance

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import cafe.adriel.broker.unsubscribe
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.databinding.ActivityMainBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.model.*
import com.mongodb.alliance.model.ChannelAdapter
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.telegram.ConnectTelegramActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.api.TdApi
import io.realm.Realm
import io.realm.kotlin.where
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.*
import org.bson.types.ObjectId
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime

@ExperimentalTime
@InternalCoroutinesApi
@AndroidEntryPoint
class ChannelsListActivity : AppCompatActivity(), GlobalBroker.Subscriber, CoroutineScope {
    private var realm: Realm = Realm.getDefaultInstance()
    private var user: User? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChannelFindAdapter
    private lateinit var chatList : TdApi.ChatList
    private var folder: FolderRealm? = null

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var binding: ActivityMainBinding

    @TelegramServ
    @Inject
    lateinit var t_service: Service

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val folderId = intent.getStringExtra("folderId")
        folder = realm.where<FolderRealm>().equalTo("_id", ObjectId(folderId)).findFirst()

        val actionbar = supportActionBar
        actionbar?.setDisplayHomeAsUpEnabled(true)
        actionbar?.setDisplayHomeAsUpEnabled(true)

        subscribe<StateChangedEvent>(lifecycleScope){ event ->
            when(event.clientState){
                ClientState.ready -> {
                    lifecycleScope.launch {
                        loadChats()
                    }
                    Toast.makeText(baseContext, "ready", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        realm = Realm.getDefaultInstance()
        recyclerView = binding.channelsList

    }

    override fun onStart() {
        super.onStart()
        try {
            user = channelApp.currentUser()
        } catch (e: IllegalStateException) {
            Timber.e(e.message)
        }
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        else {
            launch {
                val task = async {
                    withContext(Dispatchers.IO) {
                        (t_service as TelegramService).returnClientState()
                    }
                }
                val state = task.await()
                if(state == ClientState.waitParameters) {
                    withContext(Dispatchers.IO) {
                        (t_service as TelegramService).setUpClient()
                    }
                }
                if(state == ClientState.ready){
                    loadChats()
                    setUpRecyclerView(realm)
                }
            }

            setUpRecyclerView(realm)

            val config = SyncConfiguration.Builder(user!!, user!!.id)
                .waitForInitialRemoteData()
                .build()

            Realm.setDefaultConfiguration(config)

            try {
                Realm.getInstanceAsync(config, object: Realm.Callback() {
                    override fun onSuccess(realm: Realm) {
                        // since this realm should live exactly as long as this activity, assign the realm to a member variable
                        this@ChannelsListActivity.realm = realm
                        setUpRecyclerView(realm)
                    }
                })
            }
            catch(e: Exception){
                Timber.e(e.message)
            }
        }
    }

    private fun setUpRecyclerView(realm: Realm) {
        adapter = ChannelFindAdapter(
            realm.where<ChannelRealm>().sort("_id")
                .findAll(), actionBar?.title.toString()
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerView.adapter = null
        realm.close()
        unsubscribe()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                lifecycleScope.launch{
                    binding.mainProgress.visibility = View.VISIBLE
                    binding.activityMain.isEnabled = false

                    user?.logOutAsync {
                        if (it.isSuccess) {
                            realm = Realm.getDefaultInstance()

                            realm.close()

                            user = null
                        } else {
                            Timber.e("log out failed! Error: ${it.error}")
                            return@logOutAsync
                        }
                    }

                    withContext(Dispatchers.IO) {
                        (t_service as TelegramService).logOut()
                    }

                    Timber.d("user logged out")

                    binding.mainProgress.visibility = View.GONE
                    binding.activityMain.isEnabled = true
                    startActivity(Intent(baseContext, LoginActivity::class.java))
                }
                true
            }
            R.id.action_connect_telegram -> {
                lateinit var state : ClientState
                launch {
                    val task = async {
                        withContext(Dispatchers.IO) {
                            (t_service as TelegramService).returnClientState()
                        }
                    }
                    state = task.await()

                    if(state != ClientState.ready) {
                        startActivity(Intent(baseContext, ConnectTelegramActivity::class.java))
                    }
                    else{
                        Toast.makeText(baseContext, "Account already connected", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            R.id.action_refresh ->{
                lifecycleScope.launch {
                    val task = async {
                        withContext(Dispatchers.IO) {
                            (t_service as TelegramService).returnClientState()
                        }
                    }
                    val state = task.await()
                    if(state == ClientState.ready) {
                        loadChats()
                    }
                    else{
                        Toast.makeText(baseContext, "Telegram account is not connected", Toast.LENGTH_SHORT).show()
                    }
                }
                setUpRecyclerView(realm)
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }


    // FIXME: you must save chat to Realm only on selection, not on display
    suspend fun loadChats() {
        val chats = withContext(coroutineContext) {
            (t_service as TelegramService).getChats()
        }
        val nm = chats as ArrayList<TdApi.Chat>
        for (i in 0 until nm.size){

            // FIXME: show error if user is null
            val partition = user?.id.toString() ?: ""
            val channel =
                ChannelRealm(nm[i].title, partition)

            when(nm[i].type){
                is TdApi.ChatTypePrivate ->{
                    channel.typeEnum = ChannelType.chat
                    channel.folder = folder
                    if(realm.where<ChannelRealm>().equalTo("name", nm[i].title).findAll().size == 0) {
                        realm.executeTransactionAsync { realm ->
                            realm.insert(channel)
                        }
                    }
                }
                is TdApi.ChatTypeBasicGroup ->{
                    channel.typeEnum = ChannelType.groupChat
                    channel.folder = folder
                    if(realm.where<ChannelRealm>().equalTo("name", nm[i].title).findAll().size == 0) {
                        realm.executeTransactionAsync { realm ->
                            realm.insert(channel)
                        }
                    }
                }
                is TdApi.ChatTypeSupergroup ->{
                    val superg = (t_service as TelegramService).returnSupergroup((nm[i].type as TdApi.ChatTypeSupergroup).supergroupId)
                    channel.name = superg
                    channel.typeEnum = ChannelType.channel
                    channel.folder = folder
                    if(realm.where<ChannelRealm>().equalTo("name", superg).findAll().size == 0) {
                        realm.executeTransactionAsync { realm ->
                            realm.insert(channel)
                        }
                    }
                }
            }
        }
    }
}