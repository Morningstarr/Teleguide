package com.mongodb.alliance

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import cafe.adriel.broker.subscribe
import cafe.adriel.broker.unsubscribe
import com.mongodb.alliance.adapters.ChannelArrayAdapter
import com.mongodb.alliance.databinding.ActivityMainBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.model.*
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
class ChannelsArrayActivity : AppCompatActivity(), GlobalBroker.Subscriber, GlobalBroker.Publisher, CoroutineScope {
    private var realm: Realm = Realm.getDefaultInstance()
    private var user: User? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChannelArrayAdapter
    private lateinit var chatList : TdApi.ChatList
    private var folder: FolderRealm? = null
    private var ChannelsArray : ArrayList<ChannelRealm> = ArrayList()
    private var folderId : String? = null

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var binding: ActivityMainBinding

    @TelegramServ
    @Inject
    lateinit var t_service: Service

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        folderId = intent.getStringExtra("folderId")
        folder = realm.where<FolderRealm>().equalTo("_id", ObjectId(folderId)).findFirst()

        val actionbar = supportActionBar
        actionbar?.setDisplayHomeAsUpEnabled(true)
        actionbar?.setDisplayHomeAsUpEnabled(true)

        subscribe<ChannelSaveEvent>(lifecycleScope){ event ->
            if(event.parameter == 1){
                Toast.makeText(baseContext, "channel saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

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
            lifecycleScope.launch {
                binding.mainProgress.visibility = View.VISIBLE
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
                    setUpRecyclerView()
                    binding.mainProgress.visibility = View.GONE
                }
            }

            setUpRecyclerView()

        }
    }

    private fun setUpRecyclerView() {
        adapter = ChannelArrayAdapter(
            ChannelsArray, folder?.name!!
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
                setUpRecyclerView()
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


    suspend fun loadChats() {
        val chats = withContext(coroutineContext) {
            (t_service as TelegramService).getChats()
        }

        val nm = chats as ArrayList<TdApi.Chat>
        ChannelsArray = ArrayList(nm.size)
        for (i in 0 until nm.size){
            if(nm[i].title != "") {
                // FIXME: show error if user is null
                val partition = user?.id.toString() ?: ""
                val channel =
                    ChannelRealm(nm[i].title, partition)

                when (nm[i].type) {
                    is TdApi.ChatTypePrivate -> {
                        channel.typeEnum = ChannelType.chat
                    }
                    is TdApi.ChatTypeBasicGroup -> {
                        channel.typeEnum = ChannelType.groupChat
                    }
                    is TdApi.ChatTypeSupergroup -> {
                        val superg =
                            (t_service as TelegramService).returnSupergroup((nm[i].type as TdApi.ChatTypeSupergroup).supergroupId)
                        if (superg != "") {
                            channel.name = superg
                            channel.typeEnum = ChannelType.channel
                            if (realm.where<ChannelRealm>().equalTo("name", channel.name).and()
                                    .equalTo("folder._id", ObjectId(folderId)).findFirst() == null) {
                                ChannelsArray.add(channel)
                            }
                        }
                    }
                }
            }
        }
    }
}