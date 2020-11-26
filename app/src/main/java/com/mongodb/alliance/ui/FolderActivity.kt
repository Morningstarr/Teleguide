package com.mongodb.alliance.ui

import android.annotation.SuppressLint
import android.app.ActionBar
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
import com.mongodb.alliance.adapters.FolderAdapter
import com.mongodb.alliance.adapters.PinnedFolderAdapter
import com.mongodb.alliance.adapters.SimpleItemTouchHelperCallback
import com.mongodb.alliance.channelApp
import com.mongodb.alliance.databinding.ActivityFolderBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.*
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
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime


@ExperimentalTime
@InternalCoroutinesApi
@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.N)
class FolderActivity : AppCompatActivity(), GlobalBroker.Subscriber, CoroutineScope {
    private lateinit var realm: Realm
    private lateinit var recyclerView: RecyclerView
    private lateinit var pinnedRecyclerView: RecyclerView
    private lateinit var adapter: FolderAdapter
    private lateinit var pinnedAdapter: PinnedFolderAdapter
    private lateinit var fab: FloatingActionButton
    private lateinit var customActionBarView : View
    private lateinit var rootLayout : CoordinatorLayout
    private lateinit var state : ClientState
    lateinit var binding: ActivityFolderBinding
    private var user: User? = null
    private var count : Int = -1
    private var folderId : ObjectId? = null
    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    var isSelecting : Boolean = false

    @TelegramServ
    @Inject
    lateinit var tService: Service

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: SelectPinnedFolderEvent) {
        Toast.makeText(
            baseContext,
            "Открепите папку для дальнейшего взаимодействия!",
            Toast.LENGTH_LONG
        ).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: NullObjectAccessEvent) {
        Toast.makeText(baseContext, event.message, Toast.LENGTH_SHORT).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: CancelPasteSelectionEvent) {
        if(event.flag) {
            adapter.cancelPasteSelection()
        }
        else {
            pinnedAdapter.cancelPasteSelection()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: SelectFolderToMoveEvent) {
        folderId = event.folderId
    }

    @SuppressLint("WrongConstant")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: SelectFolderEvent) {
        val actionbar = supportActionBar
        isSelecting = true
        if (actionbar != null) {
            if(actionbar.customView.findViewById<TextView>(R.id.actionBar_folders_count) == null) {
                actionbar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
                actionbar.setDisplayShowCustomEnabled(true)
                actionbar.setCustomView(R.layout.action_bar_folder_options_drawable)
                customActionBarView = actionbar.customView
                if(binding.fldrFab.isOrWillBeShown) {
                    binding.fldrFab.hide()
                }
            }
            else{
                customActionBarView = actionbar.customView
            }
            val countText = customActionBarView.findViewById<TextView>(R.id.actionBar_folders_count)
            if(event.isAdd) {
                countText.text = (countText.text.toString().toInt() + 1).toString()
            }
            else{
                if(countText.text.toString().toInt() == 1){
                    setDefaultActionBar()
                    binding.fldrFab.show()
                }
                else {
                    countText.text = (countText.text.toString().toInt() - 1).toString()
                }
            }

            customActionBarView.findViewById<ImageView>(R.id.actionBar_button_cancel).setOnClickListener {
                setDefaultActionBar()
                isSelecting = false
                adapter.cancelSelection()
                binding.fldrFab.show()
            }

            customActionBarView.findViewById<ImageView>(R.id.actionBar_button_delete).setOnClickListener {
                deleteFolders()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: FolderPinDenyEvent) {
        val builder =
            AlertDialog.Builder(this)

        builder.setMessage("Нельзя закрепить более одной папки.")

        builder.setPositiveButton(
            "Открепить папку"
        ) { dialog, _ ->
            pinnedAdapter.findPinned()?.let { pinnedAdapter.setPinned(it, false) }
            event.folder?.bottomWrapper?.findViewById<ImageButton>(R.id.pin_folder)?.performClick()
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
    fun onMessageEvent(event: FolderPinEvent){
        setUpRecyclerPinned(event.pinnedFolder)
        refreshRecyclerView()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: FolderUnpinEvent){
        setUpRecyclerPinned(null)
        refreshRecyclerView()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: EditFolderEvent){
        val folderEditFragment = EditFolderFragment(event.folder)
        folderEditFragment.show(
            this.supportFragmentManager,
            folderEditFragment.tag
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: AddFolderEvent){
        refreshRecyclerView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        subscribe<OpenFolderEvent>(lifecycleScope){ event ->
            val intent = Intent(baseContext, ChannelsRealmActivity::class.java)
            intent.putExtra("folderId", event.folderId)
            startActivity(intent)
        }

        EventBus.getDefault().register(this)

        setDefaultActionBar()

        count = intent.getIntExtra("count", -1)

        binding = ActivityFolderBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        realm = Realm.getDefaultInstance()
        recyclerView = binding.foldersList

        recyclerView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if(scrollY != oldScrollY) {
                hideKeyboard()
                if(oldScrollY < 0){
                    binding.fldrFab.hide()
                }
            }
            if(!recyclerView.canScrollVertically(-1) && !isSelecting){
                binding.fldrFab.show()
            }
        }
        pinnedRecyclerView = binding.recyclerPinned
        pinnedRecyclerView.visibility = View.GONE
        fab = binding.fldrFab

        fab.setOnClickListener {
            val folderAddDialog = AddFolderFragment()
            folderAddDialog.show(
                this.supportFragmentManager,
                folderAddDialog.tag
            )
        }
    }

    @ExperimentalCoroutinesApi
    override fun onStart() {
        super.onStart()

        binding.searchView.onActionViewExpanded()
        Handler().postDelayed(Runnable { binding.searchView.clearFocus() }, 1)
        binding.fldrFab.requestFocus()

        binding.searchView.setOnQueryTextListener(object :
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

        try {
            user = channelApp.currentUser()
        } catch (e: IllegalStateException) {
            Timber.e(e)
        }
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            /*lifecycleScope.*/runBlocking {
                showLoading(true)
                //val task = async {
                    var currState = withContext(Dispatchers.IO) {
                        (tService as TelegramService).returnClientState()
                    }
                //}
                //state = task.await()
                if(currState == ClientState.waitParameters) {
                    withContext(Dispatchers.IO) {
                        (tService as TelegramService).setUpClient()
                    }
                }
                currState = withContext(Dispatchers.IO) {
                    (tService as TelegramService).returnClientState()
                }
                state = currState
            }

            val config = SyncConfiguration.Builder(user, user?.id)
                .waitForInitialRemoteData()
                .build()

            Realm.setDefaultConfiguration(config)

            try {
                Realm.getInstanceAsync(config, object : Realm.Callback() {
                    @RequiresApi(Build.VERSION_CODES.N)
                    override fun onSuccess(realm: Realm) {
                        this@FolderActivity.realm = realm
                        setUpRecyclerView(realm)
                        setUpRecyclerPinned(null)
                        showLoading(false)

                        if (count != -1) {
                            setPasteModes()
                        }
                    }
                })
            } catch (e: Exception) {
                Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
                Timber.e(e)
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        binding.searchView.onActionViewExpanded()
        Handler().postDelayed(Runnable { binding.searchView.clearFocus() }, 1)
        binding.fldrFab.requestFocus()

        binding.searchView.setOnQueryTextListener(object :
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
        
        //todo ошибка перетаскивания при перезапуске
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerView.adapter = null
        recyclerView.visibility = View.GONE
        adapter.miniaturesRefresh()
        realm.close()
    }

    override fun onBackPressed() {
        if(isSelecting) {
            setDefaultActionBar()
            isSelecting = false
            adapter.cancelSelection()
            if(binding.fldrFab.isOrWillBeHidden) {
                binding.fldrFab.show()
            }
        }
        else{
            super.onBackPressed()
        }
    }

    private fun setUpRecyclerView(realm: Realm) {
        val mutableFolders = realm.where<FolderRealm>().sort("order")
            .findAll().toMutableList()
        mutableFolders.removeIf {
            it.isPinned
        }

        if(mutableFolders.size != 0) {
            binding.foldersTextLayout.visibility = View.INVISIBLE
            adapter = FolderAdapter(
                mutableFolders, state, tService
            )

            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
            recyclerView.setHasFixedSize(true)

            val callback: ItemTouchHelper.Callback = SimpleItemTouchHelperCallback(adapter)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(recyclerView)
            recyclerView.visibility = View.VISIBLE
        }
        else{
            binding.foldersTextLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    private fun refreshRecyclerView(){
        val mutableFolders = realm.where<FolderRealm>().sort("order")
            .findAll().toMutableList()
        mutableFolders.removeIf {
            it.isPinned
        }
        adapter.setDataList(mutableFolders)
        if(mutableFolders.size <= 0){
            binding.foldersTextLayout.visibility = View.VISIBLE
        }
        else{
            if(binding.foldersTextLayout.visibility == View.VISIBLE) {
                binding.foldersTextLayout.visibility = View.INVISIBLE
            }
        }
    }

    private fun setUpRecyclerPinned(pinned: FolderRealm?){
        val found : FolderRealm?
        if(pinned == null){
            realm = Realm.getDefaultInstance()
            found = realm.where<FolderRealm>().equalTo("isPinned", true).findFirst()
            if(found != null){
                pinnedAdapter = PinnedFolderAdapter(found, state, tService)
                pinnedAdapter.addContext(this)
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
            pinnedAdapter = PinnedFolderAdapter(pinned, state, tService)
            pinnedRecyclerView.layoutManager = LinearLayoutManager(this)
            pinnedRecyclerView.adapter = pinnedAdapter
            pinnedRecyclerView.setHasFixedSize(true)
            pinnedRecyclerView.visibility = View.VISIBLE
        }
    }


    private fun showLoading(show: Boolean){
        if(!show) {
            binding.foldersProgress.visibility = View.GONE
        }
        else{
            binding.foldersProgress.visibility = View.VISIBLE
        }
    }

    @SuppressLint("WrongConstant")
    private fun setDefaultActionBar() {
        val actionbar = supportActionBar
        if (actionbar != null) {
            actionbar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            actionbar.setDisplayShowCustomEnabled(true)
            actionbar.setCustomView(R.layout.action_bar_drawable)
            customActionBarView = actionbar.customView
            customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_back).visibility =
                View.GONE
            val nameText = customActionBarView.findViewById<TextView>(R.id.name)
            nameText.text = resources.getString(R.string.app_name)
            nameText.gravity = Gravity.START
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1.0f
                gravity = Gravity.CENTER
            }
            nameText.layoutParams = params
            val scale = resources.displayMetrics.density
            val dpAsPixels = (16 * scale + 0.5f)
            nameText.setPadding(dpAsPixels.toInt(), 0, 0, 0)
            nameText.textSize = 24F

            customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_menu).setOnClickListener {
                rootLayout = binding.coordinatorFolder
                val anchor = binding.anchorFolders
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
                            setUpRecyclerView(realm)
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
                                            binding.foldersProgress.visibility = View.VISIBLE
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
                                            binding.foldersProgress.visibility = View.GONE
                                            if(checkBox.isChecked){
                                                (tService as TelegramService).logOut()
                                            }
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

    private fun deleteFolders(){
        val builder =
            AlertDialog.Builder(this)

        builder.setMessage("Вы хотите удалить выбранные папки?")

        builder.setPositiveButton(
            "Удалить"
        ) { dialog, _ ->
            adapter.deleteSelected()
            setDefaultActionBar()
            refreshRecyclerView()
            dialog.dismiss()
            binding.fldrFab.show()
        }
        builder.setNegativeButton(
            "Нет, спасибо"
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert = builder.create()
        alert.show()
    }

    private fun moveChats(folderId: ObjectId){
        val builder =
            AlertDialog.Builder(this)

        val newFolder = realm.where<FolderRealm>().equalTo("_id", folderId).findFirst()

        if (newFolder != null) {
            builder.setMessage("Вы хотите переместить выбранные чаты в папку ${newFolder.name}?")
        }

        builder.setPositiveButton(
            "Переместить"
        ) { dialog, _ ->
            if (newFolder != null) {
                EventBus.getDefault().post(MoveConfirmEvent(newFolder))
            }
            adapter.setPasteMode(false)
            pinnedAdapter.setPasteMode(false)
            dialog.dismiss()
            EventBus.getDefault().post(FinishEvent())
            finish()
            val intent = Intent(this, ChannelsRealmActivity::class.java)
            intent.putExtra("folderId", folderId.toString())
            startActivity(intent)
        }
        builder.setNegativeButton(
            "Нет, спасибо"
        ) { dialog, _ -> // Do nothing
            adapter.setPasteMode(false)
            pinnedAdapter.setPasteMode(false)
            adapter.updateItems()
            dialog.dismiss()
            finish()
        }

        val alert = builder.create()
        alert.show()
    }

    @SuppressLint("WrongConstant")
    private fun setPasteModes(){
        adapter.setPasteMode(true)
        pinnedAdapter.setPasteMode(true)
        val actionbar = supportActionBar
        if(actionbar != null) {
            actionbar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            actionbar.setDisplayShowCustomEnabled(true)
            actionbar.setCustomView(R.layout.action_bar_paste_options_drawable)
            customActionBarView = actionbar.customView

            val countText = customActionBarView.findViewById<TextView>(R.id.actionBar_chats_move_count)
            countText.text = count.toString()

            customActionBarView.findViewById<ImageView>(R.id.actionBar_button_move_ok).setOnClickListener {
                if(folderId != null){
                    folderId.let {
                        if (it != null) {
                            moveChats(it)
                        }
                    }
                }
                else{
                    Toast.makeText(
                        baseContext,
                        "Выберите папку для перемещения!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            customActionBarView.findViewById<ImageView>(R.id.actionBar_button_move_cancel).setOnClickListener {
                adapter.setPasteMode(false)
                pinnedAdapter.setPasteMode(false)
                EventBus.getDefault().post(MoveCancelEvent())
                finish()
            }
        }
    }

    private fun hideKeyboard() {
        val imm: InputMethodManager =
            this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        var view: View? = this.currentFocus
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        Handler().postDelayed(Runnable { binding.searchView.clearFocus() }, 1)
        binding.fldrFab.requestFocus()
    }

}