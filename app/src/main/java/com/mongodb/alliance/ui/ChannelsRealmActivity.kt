package com.mongodb.alliance.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.R
import com.mongodb.alliance.adapters.*
import com.mongodb.alliance.adapters.PinnedChannelAdapter
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.model.*
import com.mongodb.alliance.channelApp
import com.mongodb.alliance.databinding.ActivityChannelsRealmBinding
import com.mongodb.alliance.events.*
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.authorization.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.kotlin.where
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.*
import org.bson.types.ObjectId
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.lang.reflect.Field
import java.lang.reflect.Method
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@ExperimentalTime
@InternalCoroutinesApi
@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.N)
class ChannelsRealmActivity : AppCompatActivity(), GlobalBroker.Subscriber {
    private var realm: Realm = Realm.getDefaultInstance()
    private var user: User? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var pinnedRecyclerView: RecyclerView
    lateinit var adapter: ChannelRealmAdapter
    private lateinit var pinnedAdapter: PinnedChannelAdapter
    private lateinit var fab: FloatingActionButton
    private var folder: FolderRealm? = null
    private lateinit var binding: ActivityChannelsRealmBinding
    private var folderId : String? = null
    private lateinit var customActionBarView : View
    private lateinit var rootLayout : CoordinatorLayout
    var isSelecting : Boolean = false

    @TelegramServ
    @Inject
    lateinit var t_service: Service


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: NullObjectAccessEvent) {
        Toast.makeText(baseContext, event.message, Toast.LENGTH_SHORT).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: FinishEvent) {
        finish()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ChannelPinDenyEvent) {
        val builder =
            AlertDialog.Builder(this)

        builder.setMessage("Нельзя закрепить более одного чата.")

        builder.setPositiveButton(
            "Открепить чат"
        ) { dialog, _ ->
            pinnedAdapter.findPinned()?.let { pinnedAdapter.setPinned(it, false) }
            event.channel?.bottomWrapper?.findViewById<ImageButton>(R.id.pin_chat)?.performClick()
            setUpRecyclerPinned(null)
            refreshRecyclerView()
            dialog.dismiss()
        }
        builder.setNegativeButton(
            "Назад"
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert = builder.create()
        alert.show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ChannelPinEvent){
        setUpRecyclerPinned(event.pinnedChannel)
        refreshRecyclerView()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ChannelUnpinEvent){
        setUpRecyclerPinned(null)
        refreshRecyclerView()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: MoveCancelEvent){
        setDefaultActionBar()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: MoveConfirmEvent){
        adapter.moveChannels(event.folder)
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: SelectChatEvent){
        val actionbar = supportActionBar
        isSelecting = true
        if (actionbar != null) {
            if(actionbar.customView.findViewById<TextView>(R.id.actionBar_chats_count) == null) {
                actionbar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
                actionbar.setDisplayShowCustomEnabled(true)
                actionbar.setCustomView(R.layout.action_bar_chat_options_drawable)
                customActionBarView = actionbar.customView
            }
            else{
                customActionBarView = actionbar.customView
            }
            val countText = customActionBarView.findViewById<TextView>(R.id.actionBar_chats_count)
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
            customActionBarView.findViewById<TextView>(R.id.actionBar_folder_name).text = folder?.name

            customActionBarView.findViewById<ImageView>(R.id.actionBar_chat_button_cancel).setOnClickListener {
                setDefaultActionBar()
                isSelecting = false
                adapter.cancelSelection()
            }

            customActionBarView.findViewById<ImageView>(R.id.actionBar_chat_button_delete).setOnClickListener {
                deleteChats()
            }

            customActionBarView.findViewById<ImageView>(R.id.actionBar_chat_button_move).setOnClickListener {
                val intent = Intent(this, FolderActivity::class.java)
                intent.putExtra("count", countText.text.toString().toInt())
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        folderId = intent.getStringExtra("folderId")
        folder = realm.where<FolderRealm>().equalTo("_id", ObjectId(folderId)).findFirst() as FolderRealm

        binding = ActivityChannelsRealmBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        fab = binding.channelsFab
        recyclerView = binding.channelsInFolderList
        pinnedRecyclerView = binding.channelPinned
        pinnedRecyclerView.visibility = View.GONE

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
                    val intent = Intent(baseContext, ChannelsArrayActivity::class.java)
                    intent.putExtra("folderId", folderId)
                    startActivity(intent)
                }
                else{
                    Toast.makeText(baseContext, "Telegram account is not connected!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.channelsSearchView.onActionViewExpanded()
        Handler().postDelayed(Runnable { binding.channelsSearchView.clearFocus() }, 0)

        binding.channelsSearchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                try {
                    adapter.filter.filter(newText)
                    return false
                }
                catch(e:Exception){
                    if(e.message != "lateinit property adapter has not been initialized"){
                        Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
            }

        })

        subscribe<OpenChannelEvent>(lifecycleScope){ event ->
            startActivity(event.intent)
        }

        subscribe<SelectPinnedChatEvent>(lifecycleScope){ event ->
            Toast.makeText(baseContext, "Открепите чат для дальнейшего взаимодействия!", Toast.LENGTH_LONG).show()
        }
        EventBus.getDefault().register(this)


    }

    override fun onStart() {
        super.onStart()
        setDefaultActionBar()
        try {
            user = channelApp.currentUser()
        } catch (e: IllegalStateException) {
            Timber.e(e.message)
        }
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        else {

            val config = SyncConfiguration.Builder(user, user?.id)
                .waitForInitialRemoteData()
                .build()

            Realm.setDefaultConfiguration(config)

            try {
                Realm.getInstanceAsync(config, object: Realm.Callback() {
                    @RequiresApi(Build.VERSION_CODES.N)
                    override fun onSuccess(realm: Realm) {
                        // since this realm should live exactly as long as this activity, assign the realm to a member variable
                        this@ChannelsRealmActivity.realm = realm
                        setUpRecyclerView(realm)
                        setUpRecyclerPinned(null)
                    }
                })
            }
            catch(e: Exception){
                Timber.e(e.message)
            }
        }
    }

    override fun onBackPressed() {
        if(isSelecting) {
            setDefaultActionBar()
            isSelecting = false
            adapter.cancelSelection()
        }
        else{
            super.onBackPressed()
        }
    }

    private fun deleteChats(){
        val builder =
            AlertDialog.Builder(this)

        builder.setMessage("Вы хотите удалить выбранные чаты?")

        builder.setPositiveButton(
            "Удалить"
        ) { dialog, _ ->
            adapter.deleteSelected()
            setDefaultActionBar()
            refreshRecyclerView()
            dialog.dismiss()
        }
        builder.setNegativeButton(
            "Нет, спасибо"
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert = builder.create()
        alert.show()
    }


    private fun setUpRecyclerView(realm: Realm) {
        val query = realm.where<ChannelRealm>().equalTo("folder._id", ObjectId(folderId)).findAll().sort("order").toMutableList()
        query.removeIf {
            it.isPinned
        }
        if(query.size != 0) {
            binding.textLayout.visibility = View.INVISIBLE
            adapter = ChannelRealmAdapter(
                query
            )

            adapter.initializeTService(t_service as TelegramService)
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
            folderId?.let { adapter.setFolderId(it) }
            recyclerView.setHasFixedSize(true)

            val callback: ItemTouchHelper.Callback = SimpleItemTouchHelperCallback(adapter)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(recyclerView)

        }
        else{
            binding.textLayout.visibility = View.VISIBLE
        }

    }

    open fun refreshRecyclerView(){
        val mutableChannels = realm.where<ChannelRealm>().equalTo("folder._id", ObjectId(folderId)).findAll().sort("order").toMutableList()
        mutableChannels.removeIf {
            it.isPinned
        }
        adapter.setDataList(mutableChannels)
        if(mutableChannels.size <= 0){
            binding.textLayout.visibility = View.VISIBLE
        }
    }

    private fun setUpRecyclerPinned(pinned: ChannelRealm?){
        val found : ChannelRealm?
        if(pinned == null){
            realm = Realm.getDefaultInstance()
            found = realm.where<ChannelRealm>().equalTo("folder._id", ObjectId(folderId)).equalTo("isPinned", true).findFirst()
            if(found != null){
                pinnedAdapter = PinnedChannelAdapter(found)
                pinnedAdapter.initializeTService(t_service as TelegramService)
                pinnedAdapter.addContext(this)
                folderId?.let { pinnedAdapter.setFolderId(it) }
                pinnedRecyclerView.layoutManager = LinearLayoutManager(this)
                pinnedRecyclerView.adapter = pinnedAdapter
                pinnedRecyclerView.setHasFixedSize(true)
                pinnedRecyclerView.visibility = View.VISIBLE
            }
            else{
                pinnedRecyclerView.adapter = null
                pinnedRecyclerView.visibility = View.GONE
            }
        }
        else {
            pinnedAdapter = PinnedChannelAdapter(pinned)
            pinnedAdapter.initializeTService(t_service as TelegramService)
            folderId?.let { pinnedAdapter.setFolderId(it) }
            pinnedRecyclerView.layoutManager = LinearLayoutManager(this)
            pinnedRecyclerView.adapter = pinnedAdapter
            pinnedRecyclerView.setHasFixedSize(true)
            pinnedRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showLoading(show : Boolean){
        if (show) {
            binding.channelsInFolderProgress.visibility = View.VISIBLE
        }
        else {
            binding.channelsInFolderProgress.visibility = View.GONE
        }
    }


    private fun setDefaultActionBar() {
        val actionbar = supportActionBar
        if (actionbar != null) {
            actionbar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            actionbar.setDisplayShowCustomEnabled(true)
            actionbar.setCustomView(R.layout.action_bar_drawable)
            customActionBarView = actionbar.customView
            val nameText = customActionBarView.findViewById<TextView>(R.id.name)
            nameText.text = folder?.name

            customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_back).setOnClickListener {
                finish()
            }

            customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_menu).setOnClickListener {
                rootLayout = binding.coordinatorChannelsArray
                val anchor = binding.anchorChannelsArray
                val wrapper = ContextThemeWrapper(
                    this,
                    R.style.MyPopupMenu
                )
                val popup = PopupMenu(wrapper, anchor, Gravity.END)

                try {
                    val fields: Array<Field> = popup.javaClass.declaredFields
                    for (field in fields) {
                        if ("mPopup" == field.name) {
                            field.isAccessible = true
                            val menuPopupHelper: Any = field.get(popup)
                            val classPopupHelper =
                                Class.forName(menuPopupHelper.javaClass.name)
                            val setForceIcons: Method = classPopupHelper.getMethod(
                                "setForceShowIcon",
                                Boolean::class.javaPrimitiveType
                            )
                            setForceIcons.invoke(menuPopupHelper, true)
                            break
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e.message)
                }

                popup.menuInflater.inflate(R.menu.menu, popup.menu)
                popup.show()

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_profile -> {
                            startActivity(Intent(this, ProfileActivity::class.java))
                        }
                        R.id.action_logout -> {
                            try {
                                lifecycleScope.launch {
                                    binding.channelsInFolderProgress.visibility = View.VISIBLE

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

                                    Timber.d("user logged out")

                                    binding.channelsInFolderProgress.visibility = View.GONE
                                    startActivity(
                                        Intent(
                                            baseContext,
                                            LoginActivity::class.java
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                    true
                }
            }
        }
    }

}