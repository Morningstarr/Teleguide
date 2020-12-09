package com.mongodb.alliance.ui

import android.annotation.SuppressLint
import android.app.ActionBar
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import cafe.adriel.broker.unsubscribe
import com.mongodb.alliance.R
import com.mongodb.alliance.adapters.ChannelArrayAdapter
import com.mongodb.alliance.channelApp
import com.mongodb.alliance.databinding.ActivityChannelsArrayBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.ChannelSaveEvent
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.events.SelectChatFromArrayEvent
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.ChannelType
import com.mongodb.alliance.model.FolderRealm
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.authorization.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.api.TdApi
import io.realm.Realm
import io.realm.kotlin.where
import io.realm.mongodb.User
import kotlinx.coroutines.*
import org.bson.types.ObjectId
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
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
    private lateinit var customActionBarView : View
    var isSelecting : Boolean = false
    private lateinit var rootLayout : CoordinatorLayout

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var binding: ActivityChannelsArrayBinding

    @TelegramServ
    @Inject
    lateinit var tService: Service

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: NullObjectAccessEvent) {
        Toast.makeText(baseContext, event.message, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("WrongConstant")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: SelectChatFromArrayEvent) {
        val actionbar = supportActionBar
        isSelecting = true
        if (actionbar != null) {
            customActionBarView = actionbar.customView
            val countText = customActionBarView.findViewById<TextView>(R.id.actionBar_chats_move_count)
            if(event.isAdd) {
                countText.text = (countText.text.toString().toInt() + 1).toString()
            }
            else{
                if(countText.text.toString().toInt() == 1){
                    setDefaultActionBar()
                }
                else {
                    countText.text = (countText.text.toString().toInt() - 1).toString()
                }
            }

            val btnBack = customActionBarView.findViewById<ImageView>(R.id.actionBar_button_move_back)
            val btnCancel = customActionBarView.findViewById<ImageView>(R.id.actionBar_button_move_cancel)
            val btnOk = customActionBarView.findViewById<ImageView>(R.id.actionBar_button_move_ok)

            btnBack.visibility = View.GONE
            btnCancel.visibility = View.VISIBLE
            countText.visibility = View.VISIBLE
            btnOk.visibility = View.VISIBLE

            btnCancel.setOnClickListener {
                setDefaultActionBar()
                isSelecting = false
                btnBack.visibility = View.VISIBLE
                btnCancel.visibility = View.GONE
                countText.visibility = View.GONE
                adapter.cancelSelection()
            }

            btnOk.setOnClickListener {
                adapter.addToFolder(ObjectId(folderId))
                finish()
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setDefaultActionBar()

        folderId = intent.getStringExtra("folderId")
        folder = realm.where<FolderRealm>().equalTo("_id", ObjectId(folderId)).findFirst()

        subscribe<ChannelSaveEvent>(lifecycleScope){ event ->
            if(event.parameter == 1){
                Toast.makeText(baseContext, "Channel saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        EventBus.getDefault().register(this)

        binding = ActivityChannelsArrayBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        recyclerView = binding.channelsList

        binding.channelsArrSearchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                try {
                    adapter.filter.filter(newText)
                    return false
                } catch (e: Exception) {
                    if (e.message != "lateinit property adapter has not been initialized") {
                        Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
            }
        })

        binding.channelsArrSearchView.onActionViewExpanded()
        Handler().postDelayed(Runnable { binding.channelsArrSearchView.clearFocus() }, 0)
    }

    override fun onRestart() {
        super.onRestart()
        binding.channelsArrSearchView.onActionViewExpanded()
        Handler().postDelayed(Runnable { binding.channelsArrSearchView.clearFocus() }, 0)

    }

    @ExperimentalCoroutinesApi
    override fun onStart() {
        super.onStart()
        try {
            user = channelApp.currentUser()
        } catch (e: IllegalStateException) {
            Timber.e(e)
        }
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        else {
            lifecycleScope.launch {
                binding.mainProgress.visibility = View.VISIBLE
                val task = async {
                    withContext(Dispatchers.IO) {
                        (tService as TelegramService).returnClientState()
                    }
                }
                val state = task.await()
                if(state == ClientState.waitParameters) {
                    withContext(Dispatchers.IO) {
                        (tService as TelegramService).setUpClient()
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
        val adp = folder?.name?.let { ChannelArrayAdapter(ChannelsArray) }
        if (adp != null) {
            adapter = adp
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
            recyclerView.setHasFixedSize(true)
            recyclerView.addItemDecoration(
                DividerItemDecoration(
                    this,
                    DividerItemDecoration.VERTICAL
                )
            )
        } else {
            Toast.makeText(baseContext, "Folder data error!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerView.adapter = null
        realm.close()
        unsubscribe()
    }

    private suspend fun loadChats() {
        val chats = withContext(coroutineContext) {
            (tService as TelegramService).getChats()
        }

        val nm = chats as ArrayList<TdApi.Chat>
        ChannelsArray = ArrayList(nm.size)
        for (i in 0 until nm.size){
            if(nm[i].title != "") {
                // FIXME: show error if user is null
                val partition = user?.id.toString()
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
                            (tService as TelegramService).returnSupergroup((nm[i].type as TdApi.ChatTypeSupergroup).supergroupId)
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

                channel.displayName = nm[i].title
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun setDefaultActionBar() {
        val actionbar = supportActionBar
        if (actionbar != null) {
            actionbar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            actionbar.setDisplayShowCustomEnabled(true)
            actionbar.setCustomView(R.layout.action_bar_paste_options_drawable)

            val actionBarView = actionbar.customView

            val middleText = actionBarView.findViewById<TextView>(R.id.actionBar_move_to)
            val btnBack = actionBarView.findViewById<ImageView>(R.id.actionBar_button_move_back)
            val btnOk = actionBarView.findViewById<ImageView>(R.id.actionBar_button_move_ok)
            val countText = actionBarView.findViewById<TextView>(R.id.actionBar_chats_move_count)
            val btnCancel = actionBarView.findViewById<ImageView>(R.id.actionBar_button_move_cancel)

            lifecycleScope.launch {
                val task = async {
                    withContext(Dispatchers.IO){
                        (tService as TelegramService).getProfileName()
                    }
                }
                middleText.text = task.await()
            }

            btnBack.visibility = View.VISIBLE
            countText.visibility = View.GONE
            btnOk.visibility = View.INVISIBLE
            btnCancel.visibility = View.GONE

            btnBack.setOnClickListener {
                finish()
            }
        }
    }
}