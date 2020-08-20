package com.mongodb.alliance

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
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
import com.mongodb.alliance.model.ChannelAdapter
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.StateChangedEvent
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.telegram.ConnectTelegramActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.api.TdApi
import io.realm.Realm
import io.realm.kotlin.where
import io.realm.mongodb.User
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime

@ExperimentalTime
@InternalCoroutinesApi
@AndroidEntryPoint
class ChannelsActivity : AppCompatActivity(), GlobalBroker.Subscriber, CoroutineScope {
    private lateinit var realm: Realm
    private var user: User? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChannelAdapter
    private lateinit var fab: FloatingActionButton
    private lateinit var chatList : TdApi.ChatList

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var binding: ActivityMainBinding

    @TelegramServ
    @Inject
    lateinit var t_service: Service

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        subscribe<StateChangedEvent>(lifecycleScope){ event ->
            when(event.clientState){
                ClientState.setParameters -> {
                    //loadChats()
                    Toast.makeText(baseContext, "parameters set", Toast.LENGTH_SHORT).show()
                    //unsubscribe()
                }
                ClientState.ready -> {
                    //TODO load_chats
                    unsubscribe()
                    loadChats()
                    Toast.makeText(baseContext, "ready", Toast.LENGTH_SHORT).show()

                }
                else -> {
                    //unsubscribe()
                    job.cancelAndJoin()
                    startActivity(Intent(this, ConnectTelegramActivity::class.java))

                }
                /*ClientState.waitNumber -> {
                    startActivity(Intent(this, ConnectTelegramActivity::class.java))
                }*/
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        realm = Realm.getDefaultInstance()
        recyclerView = binding.channelsList
        fab = binding.floatingActionButton

        fab.setOnClickListener {
            val input = EditText(this)
            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setMessage("Enter channel name:")
                .setCancelable(true)
                .setPositiveButton("Add") { dialog, _ -> run {
                    dialog.dismiss()
                    try {
                        val channel =
                            ChannelRealm(input.text.toString())
                        realm.executeTransactionAsync { realm ->
                            realm.insert(channel)
                        }
                    }
                    catch(exception: Exception){
                        Toast.makeText(baseContext, exception.message, Toast.LENGTH_SHORT).show()
                    }
                }
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel()
                }

            val dialog = dialogBuilder.create()
            dialog.setView(input)
            dialog.setTitle("Add New Channel")
            dialog.show()
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            user = channelApp.currentUser()
        } catch (e: IllegalStateException) {
            Log.w(TAG(), e)
        }
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        else {
            launch {
                withContext(Dispatchers.IO) {
                    (t_service as TelegramService).setUpClient()
                    t_service.initService()
                }
            }

            //startActivity(Intent(this, ConnectTelegramActivity::class.java))

            /*val config = SyncConfiguration.Builder(user!!, "New Folder")
                .waitForInitialRemoteData()
                .build()

            Realm.setDefaultConfiguration(config)*/


            try {
                //исключение вылетает здесь
                /*Realm.getInstanceAsync(config, object: Realm.Callback() {
                    override fun onSuccess(realm: Realm) {
                        // since this realm should live exactly as long as this activity, assign the realm to a member variable
                        this@ChannelsActivity.realm = realm
                        setUpRecyclerView(realm)
                    }
                })*/
            }
            catch(e: Exception){
                Log.v(TAG(), "здесь")
            }
        }
    }

    private fun setUpRecyclerView(realm: Realm) {
        adapter = ChannelAdapter(
            realm.where<ChannelRealm>().sort("_id")
                .findAll()
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
                user?.logOutAsync {
                    if (it.isSuccess) {
                        realm.close()
                        user = null
                        Log.v(TAG(), "user logged out")
                        startActivity(Intent(this, LoginActivity::class.java))
                    } else {
                        Log.e(TAG(), "log out failed! Error: ${it.error}")
                    }
                }
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    suspend fun loadChats() {
        val chats = withContext(lifecycleScope.coroutineContext) {
            (t_service as TelegramService).getChats()
        }

        val nm = chats as ArrayList<TdApi.Chat>
        //val names = returnChatNames(nm)
        for (i in 0 until nm.size){
            if(realm.where<ChannelRealm>().equalTo("name", nm[i].title).findAll().size == 0) {
                val channel =
                    ChannelRealm(nm[i].title, "Folder")

                realm.executeTransactionAsync { realm ->
                    realm.insert(channel)
                }
            }
        }
        setUpRecyclerView(realm)
    }

    fun returnChatNames(chats: ArrayList<TdApi.Chat>) : ArrayList<String> {
        lateinit var chatNames : ArrayList<String>
        for(i in 0 until chats.size) {
            chatNames.add((chats[i]).title)
        }

        return chatNames
    }
}