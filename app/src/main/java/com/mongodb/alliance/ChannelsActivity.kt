package com.mongodb.alliance

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.databinding.ActivityChannelsBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.model.*
import com.mongodb.alliance.adapters.ChannelAdapter
import com.mongodb.alliance.adapters.FolderAdapter
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.api.TdApi
import io.realm.Realm
import io.realm.kotlin.where
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.*
import org.bson.types.ObjectId
import timber.log.Timber
import java.nio.channels.Channel
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.ExperimentalTime

@ExperimentalTime
@InternalCoroutinesApi
@AndroidEntryPoint
class ChannelsActivity : AppCompatActivity(), GlobalBroker.Subscriber {
    private var realm: Realm = Realm.getDefaultInstance()
    private var user: User? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChannelAdapter
    private lateinit var fab: FloatingActionButton
    private var folder: FolderRealm? = null
    private lateinit var binding: ActivityChannelsBinding
    private var folderId : String? = null

    @TelegramServ
    @Inject
    lateinit var t_service: Service

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        folderId = intent.getStringExtra("folderId")
        val folder = realm.where<FolderRealm>().equalTo("_id", ObjectId(folderId)).findFirst()
        val actionbar = supportActionBar
        actionbar?.title = folder?.name
        actionbar?.setDisplayHomeAsUpEnabled(true)
        actionbar?.setDisplayHomeAsUpEnabled(true)

        binding = ActivityChannelsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        fab = binding.channelsFab
        recyclerView = binding.channelsInFolderList

        fab.setOnClickListener {
            lifecycleScope.launch {
                showLoading(true)
                val task = async {
                    withContext(Dispatchers.IO) {
                        (t_service as TelegramService).returnClientState()
                    }
                }
                val state = task.await()
                showLoading(false)
                if(state == ClientState.ready) {
                    val intent = Intent(baseContext, ChannelsListActivity::class.java)
                    intent.putExtra("folderId", folderId)
                    startActivity(intent)
                }
                else{
                    Toast.makeText(baseContext, "Telegram account is not connected!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        subscribe<OpenChannelEvent>(lifecycleScope){ event ->
            startActivity(event.intent)
        }
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
            val config = SyncConfiguration.Builder(user!!, user!!.id)
                .waitForInitialRemoteData()
                .build()

            Realm.setDefaultConfiguration(config)

            try {
                Realm.getInstanceAsync(config, object: Realm.Callback() {
                    override fun onSuccess(realm: Realm) {
                        // since this realm should live exactly as long as this activity, assign the realm to a member variable
                        this@ChannelsActivity.realm = realm
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
        adapter = ChannelAdapter(
            realm.where<ChannelRealm>().sort("_id").findAll()
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    }

    private fun showLoading(show : Boolean){
        if (show) {
            binding.channelsInFolderProgress.visibility = View.VISIBLE
        }
        else {
            binding.channelsInFolderProgress.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    /*suspend fun loadChats() {
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
    }*/
}