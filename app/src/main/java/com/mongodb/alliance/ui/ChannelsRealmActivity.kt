package com.mongodb.alliance.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.R
import com.mongodb.alliance.adapters.ChannelRealmAdapter
import com.mongodb.alliance.adapters.PinnedChannelAdapter
import com.mongodb.alliance.adapters.SimpleItemTouchHelperCallback
import com.mongodb.alliance.channelApp
import com.mongodb.alliance.databinding.ActivityChannelsRealmBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.*
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.FolderRealm
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
    private lateinit var state : ClientState
    lateinit var adapter: ChannelRealmAdapter
    private lateinit var pinnedAdapter: PinnedChannelAdapter
    private lateinit var fab: FloatingActionButton
    private var folder: FolderRealm? = null
    private lateinit var binding: ActivityChannelsRealmBinding
    private var folderId : String? = null
    private lateinit var customActionBarView : View
    private lateinit var rootLayout : CoordinatorLayout
    private var callb : SimpleItemTouchHelperCallback? = null
    private var touchHelper : ItemTouchHelper? = null
    var isSelecting : Boolean = false


    @TelegramServ
    @Inject
    lateinit var tService: Service


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
        binding.channelsFab.show()
        isSelecting = false
        adapter.cancelSelection()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: MoveConfirmEvent){
        adapter.moveChannels(event.folder)
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: SelectChatEvent){
        val actionbar = supportActionBar
        isSelecting = true
        binding.channelsFab.hide()
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
                    isSelecting = false
                    adapter.cancelSelection()
                    binding.channelsFab.show()
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
                binding.channelsFab.show()
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

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        folderId = intent.getStringExtra("folderId")
        folder = realm.where<FolderRealm>().equalTo("_id", ObjectId(folderId)).findFirst() as FolderRealm

        binding = ActivityChannelsRealmBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        fab = binding.channelsFab
        recyclerView = binding.channelsInFolderList
        recyclerView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if(scrollY != oldScrollY) {
                hideKeyboard()
                if(oldScrollY < 0){
                    binding.channelsFab.hide()
                }
            }
            if(!recyclerView.canScrollVertically(-1) && !isSelecting){
                binding.channelsFab.show()
            }
        }
        pinnedRecyclerView = binding.channelPinned
        pinnedRecyclerView.visibility = View.GONE

        fab.setOnClickListener {
            lifecycleScope.launch {
                showLoading(true)
                val task = async {
                    withContext(Dispatchers.IO) {
                        (tService as TelegramService).returnClientState()
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
                    connectionOffering()
                }
            }
        }

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

        binding.channelsSearchView.onActionViewExpanded()
        Handler().postDelayed(Runnable { binding.channelsSearchView.clearFocus() }, 0)

        subscribe<OpenChannelEvent>(lifecycleScope){ event ->
            startActivity(event.intent)
        }

        subscribe<SelectPinnedChatEvent>(lifecycleScope){
            Toast.makeText(baseContext, "Открепите чат для дальнейшего взаимодействия!", Toast.LENGTH_LONG).show()
        }
        EventBus.getDefault().register(this)


    }

    @ExperimentalCoroutinesApi
    override fun onStart() {
        super.onStart()
        setDefaultActionBar()
        try {
            user = channelApp.currentUser()
        } catch (e: IllegalStateException) {
            Timber.e(e)
        }
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        else {
            runBlocking {
                showLoading(true)
                withContext(Dispatchers.IO) {
                    val task = async {
                        (tService as TelegramService).returnClientState()
                    }
                    state = task.await()
                    if (state == ClientState.waitParameters) {
                        (tService as TelegramService).setUpClient()
                        state = (tService as TelegramService).returnClientState()
                    }
                    if(state == ClientState.ready) {
                        val tsk = async {
                            (tService as TelegramService).fillChats()
                        }
                        tsk.await()
                    }
                }
                recyclerView.visibility = View.VISIBLE
            }

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
                        showLoading(false)
                    }
                })
            }
            catch(e: Exception){
                Timber.e(e)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        touchHelper?.attachToRecyclerView(null)
    }

    override fun onBackPressed() {
        if(isSelecting) {
            setDefaultActionBar()
            isSelecting = false
            adapter.cancelSelection()
            binding.channelsFab.show()
        }
        else{
            super.onBackPressed()
        }
    }

    private fun connectionOffering(){
        val builder =
            AlertDialog.Builder(this)

        builder.setMessage("Чтобы добавить новые чаты, необходимо войти в Telegram account.")

        builder.setPositiveButton(
            "Войти"
        ) { _, _ ->
            val intent = Intent(baseContext, ProfileActivity::class.java)
            intent.putExtra("beginConnection", true)
            startActivity(intent)
        }
        builder.setNegativeButton(
            "Нет, спасибо"
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert = builder.create()
        alert.show()
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
            binding.channelsTextLayout.visibility = View.INVISIBLE
            adapter = ChannelRealmAdapter(
                query, state, tService
            )

            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
            folderId?.let { adapter.setFolderId(it) }
            recyclerView.setHasFixedSize(true)

            callb = SimpleItemTouchHelperCallback(adapter)
            touchHelper = ItemTouchHelper(callb!!)
            touchHelper!!.attachToRecyclerView(recyclerView)

        }
        else{
            binding.channelsTextLayout.visibility = View.VISIBLE
            //binding.channelsSearchView.onActionViewExpanded()
            //Handler().postDelayed(Runnable { binding.channelsSearchView.clearFocus() }, 0)
            recyclerView.visibility = View.GONE
            if(touchHelper != null) {
                touchHelper!!.attachToRecyclerView(null)
            }
        }

    }

    private fun refreshRecyclerView(){
        val mutableChannels = realm.where<ChannelRealm>().equalTo("folder._id", ObjectId(folderId)).findAll().sort("order").toMutableList()
        mutableChannels.removeIf {
            it.isPinned
        }
        try {
            adapter.setDataList(mutableChannels)
            if (mutableChannels.size <= 0) {
                binding.channelsTextLayout.visibility = View.VISIBLE
            }
            else{
                if (binding.channelsTextLayout.visibility == View.VISIBLE) {
                    binding.channelsTextLayout.visibility = View.INVISIBLE
                }
            }
        }
        catch(e:UninitializedPropertyAccessException){
            binding.channelsTextLayout.visibility = View.VISIBLE
        }

    }

    private fun setUpRecyclerPinned(pinned: ChannelRealm?){
        val found : ChannelRealm?
        if(pinned == null){
            realm = Realm.getDefaultInstance()
            found = realm.where<ChannelRealm>().equalTo("folder._id", ObjectId(folderId)).equalTo("isPinned", true).findFirst()
            if(found != null){
                pinnedAdapter = PinnedChannelAdapter(found, state, tService)
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
            pinnedAdapter = PinnedChannelAdapter(pinned, state, tService)
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
                    Timber.e(e)
                }

                popup.menuInflater.inflate(R.menu.chats_folders_menu, popup.menu)
                popup.show()

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.chats_folders_action_profile -> {
                            startActivity(Intent(this, ProfileActivity::class.java))
                        }
                        R.id.chats_folders_action_refresh -> {
                            //setUpRecyclerView(realm)
                            refreshRecyclerView()
                            setUpRecyclerPinned(null)
                        }
                        R.id.chats_folders_action_exit -> {
                            try {
                                val checkBoxView =
                                    View.inflate(this, R.layout.check_frame_layout, null)
                                val checkBox =
                                    checkBoxView.findViewById<View>(R.id.alert_checkBox) as CheckBox
                                checkBox.setOnCheckedChangeListener { _, _ ->
                                    // Save to shared preferences
                                }

                                val builder = AlertDialog.Builder(this)
                                builder.setTitle("")
                                builder.setMessage("Чтобы добавить новые чаты необходимо будет войти в Telegram аккаунт.")
                                    .setView(checkBoxView)
                                    .setCancelable(true)
                                    .setPositiveButton(
                                        "Выйти"
                                    ) { _, _ ->
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
                                            if(checkBox.isChecked){
                                                (tService as TelegramService).logOut()
                                            }
                                            finish()
                                            startActivity(
                                                Intent(
                                                    baseContext,
                                                    LoginActivity::class.java
                                                )
                                            )
                                        }
                                    }
                                    .setNegativeButton(
                                        "Отмена"
                                    ) { dialog, _ ->
                                        dialog.cancel()
                                    }.show()
                            }
                            catch (e:Exception){

                            }
                        }
                    }
                    true
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm: InputMethodManager =
            this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        //Find the currently focused view, so we can grab the correct window token from it.
        var view: View? = this.currentFocus
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        Handler().postDelayed(Runnable { binding.channelsSearchView.clearFocus() }, 0)
    }

}