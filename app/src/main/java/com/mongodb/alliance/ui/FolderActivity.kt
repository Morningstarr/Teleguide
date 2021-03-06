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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
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
import com.mongodb.alliance.ui.telegram.CodeFragment
import com.mongodb.alliance.ui.telegram.NewPhoneNumberFragment
import com.mongodb.alliance.ui.telegram.PasswordFragment
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
    private var callb : SimpleItemTouchHelperCallback? = null
    private var touchHelper : ItemTouchHelper? = null
    lateinit var binding: ActivityFolderBinding
    private var bottomSheetFragment: BottomSheetDialogFragment? = null
    private var user: User? = null
    private var count : Int = -1
    private var folderId : ObjectId? = null
    private var job: Job = Job()
    private var isResumed = false
    private var isLogin = false
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
            "?????????????????? ?????????? ?????? ?????????????????????? ????????????????????????????!",
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
                    isSelecting = false
                    adapter.cancelSelection()
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

        builder.setMessage("???????????? ?????????????????? ?????????? ?????????? ??????????.")

        builder.setPositiveButton(
            "?????????????????? ??????????"
        ) { dialog, _ ->
            val pinned = pinnedAdapter.findPinned()
            pinned?.let { pinnedAdapter.setPinned(it, false) }
            event.folderObj?.let { pinnedAdapter.setPinned(it, true) }

            unPinFolder(pinned)
            pinFolder(event.folderObj)


            dialog.dismiss()
        }
        builder.setNegativeButton(
            "??????????"
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert = builder.create()
        alert.show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: FolderPinEvent){
        pinFolder(event.pinnedFolder)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: FolderUnpinEvent){
        unPinFolder(event.folder)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: EditFolderEvent){
        val folderEditFragment = EditFolderFragment(event.folder)
        folderEditFragment.show(
            this.supportFragmentManager,
            folderEditFragment.tag
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        subscribe<OpenFolderEvent>(lifecycleScope){ event ->
            val intent = Intent(baseContext, ChannelsRealmActivity::class.java)
            intent.putExtra("folderId", event.folderId)
            startActivity(intent)
        }

        subscribe<AddFolderEvent>(lifecycleScope){ event ->
            try {
                if (adapter.foldersFilterList.size > 0) {
                    adapter.foldersFilterList.add(event.folder)
                    adapter.notifyItemInserted(event.folder.order)
                } else {
                    adapter.foldersFilterList.add(event.folder)
                    adapter.notifyDataSetChanged()
                }
            }
            catch(e:UninitializedPropertyAccessException){
                setUpRecyclerView(realm)
            }

            recyclerView.visibility = View.VISIBLE
            binding.foldersTextLayout.visibility = View.INVISIBLE
            binding.searchView.onActionViewExpanded()
            Handler().postDelayed(Runnable { binding.searchView.clearFocus() }, 0)
        }

        subscribe<FolderEditEvent>(lifecycleScope){
            adapter.notifyDataSetChanged()
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

        binding.searchView.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                try {
                    adapter.filter.filter(newText)
                    if(newText != "") {
                        binding.searchView.findViewById<ImageView>(R.id.search_mag_icon).visibility =
                            View.GONE
                    }
                    else{
                        binding.searchView.findViewById<ImageView>(R.id.search_mag_icon).visibility =
                            View.VISIBLE
                    }
                    return false
                } catch (e: Exception) {
                    if (e.message != "lateinit property adapter has not been initialized") {
                        Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
            }
        })

        binding.searchView.onActionViewExpanded()
        Handler().postDelayed(Runnable { binding.searchView.clearFocus() }, 0)

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
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            isLogin = true
        } else {
            runBlocking {
                showLoading(true)
                    var currState = withContext(Dispatchers.IO) {
                        (tService as TelegramService).returnClientState()
                    }

                if(currState == ClientState.waitParameters) {
                    withContext(Dispatchers.IO) {
                        (tService as TelegramService).setUpClient()
                    }
                }
                currState = withContext(Dispatchers.IO) {
                    (tService as TelegramService).returnClientState()
                }
                state = currState
                when(state){
                    ClientState.ready -> {
                        val tsk = async {
                            (tService as TelegramService).fillChats()
                        }
                        tsk.await()
                    }
                    ClientState.waitNumber -> {
                        if (!isResumed && !isLogin) {
                            showNotConnectedAlert()
                        }
                    }
                    ClientState.waitCode -> {
                        if (!isResumed && !isLogin) {
                            showNotFinishedAlert()
                        }
                    }
                    ClientState.waitPassword -> {
                        if (!isResumed && !isLogin) {
                            showNotFinishedAlert()
                        }
                    }
                    else -> { Toast.makeText(baseContext, state.displayName, Toast.LENGTH_SHORT).show() }
                }
            }

            val config = SyncConfiguration.Builder(user, user?.id)
                .allowQueriesOnUiThread(true)
                .allowWritesOnUiThread(true)
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

    override fun onResume() {
        super.onResume()
        isResumed = true
    }

    override fun onStop() {
        super.onStop()
        touchHelper?.attachToRecyclerView(null)
    }

    override fun onRestart() {
        super.onRestart()
        binding.searchView.onActionViewExpanded()
        Handler().postDelayed(Runnable { binding.searchView.clearFocus() }, 0)

    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerView.adapter = null
        recyclerView.visibility = View.GONE
        realm.close()
        binding.searchView.setQuery("", false)
        binding.searchView.clearFocus()
    }

    override fun onPause() {
        super.onPause()
        binding.searchView.setQuery("", false)
        binding.searchView.clearFocus()
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
            if(adapter.isPaste){
                adapter.setPasteMode(false)
                pinnedAdapter.setPasteMode(false)
                EventBus.getDefault().post(MoveCancelEvent())
                finish()
            }
            super.onBackPressed()
        }
    }

    private fun setUpRecyclerView(realm: Realm, currSt: ClientState = ClientState.undefined) {
        val mutableFolders = realm.where<FolderRealm>().sort("order")
            .findAll().toMutableList()
        mutableFolders.removeIf {
            it.isPinned
        }

        if(mutableFolders.size != 0) {
            binding.foldersTextLayout.visibility = View.INVISIBLE
            if(currSt == ClientState.undefined) {
                adapter = FolderAdapter(
                    mutableFolders, state, tService
                )
            }
            else{
                adapter = FolderAdapter(
                    mutableFolders, currSt, tService
                )
            }

            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
            recyclerView.setHasFixedSize(true)

            callb = SimpleItemTouchHelperCallback(adapter)
            touchHelper = ItemTouchHelper(callb!!)
            touchHelper!!.attachToRecyclerView(recyclerView)

            recyclerView.visibility = View.VISIBLE
        }
        else{
            binding.searchView.onActionViewExpanded()
            Handler().postDelayed(Runnable { binding.searchView.clearFocus() }, 0)

            binding.foldersTextLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            if(touchHelper != null) {
                touchHelper!!.attachToRecyclerView(null)
            }

        }
    }

    private fun refreshRecyclerView(){
        val mutableFolders = realm.where<FolderRealm>().sort("order")
            .findAll().toMutableList()
        mutableFolders.removeIf {
            it.isPinned
        }
        try {
            adapter.setDataList(mutableFolders)
            if (mutableFolders.size <= 0) {
                binding.foldersTextLayout.visibility = View.VISIBLE
            } else {
                if (binding.foldersTextLayout.visibility == View.VISIBLE) {
                    binding.foldersTextLayout.visibility = View.INVISIBLE
                }
            }
        }
        catch(e:UninitializedPropertyAccessException){
            binding.foldersTextLayout.visibility = View.VISIBLE
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
                            refreshRecyclerView()
                            setUpRecyclerPinned(null)
                            binding.searchView.onActionViewExpanded()
                            Handler().postDelayed(Runnable { binding.searchView.clearFocus() }, 0)
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
                                builder.setMessage("?????????? ???????????????? ?????????? ???????? ???????????????????? ?????????? ?????????? ?? Telegram ??????????????.")
                                    .setView(checkBoxView)
                                    .setCancelable(true)
                                    .setPositiveButton(
                                        "??????????"
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
                                            if (checkBox.isChecked) {
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
                                        "????????????"
                                    ) { dialog, _ ->
                                        dialog.cancel()
                                    }.show()
                            } catch (e: Exception) {

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

        builder.setMessage("???? ???????????? ?????????????? ?????????????????? ???????????")

        builder.setPositiveButton(
            "??????????????"
        ) { dialog, _ ->
            adapter.deleteSelected()
            setDefaultActionBar()
            dialog.dismiss()
            binding.fldrFab.show()
            if(adapter.foldersFilterList.size == 0) {
                recyclerView.visibility = View.GONE
                binding.foldersTextLayout.visibility = View.VISIBLE
                binding.searchView.onActionViewExpanded()
                Handler().postDelayed(Runnable { binding.searchView.clearFocus() }, 0)
            }
            isSelecting = false
            adapter.cancelSelection()
        }
        builder.setNegativeButton(
            "??????, ??????????????"
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
            builder.setMessage("???? ???????????? ?????????????????????? ?????????????????? ???????? ?? ?????????? ${newFolder.name}?")
        }

        builder.setPositiveButton(
            "??????????????????????"
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
            "??????, ??????????????"
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
                        "???????????????? ?????????? ?????? ??????????????????????!",
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

    @ExperimentalCoroutinesApi
    private fun showNotFinishedAlert(){
        val builder =
            AlertDialog.Builder(this)

        builder.setMessage("?? ?????????????? ?????? ???? ???? ?????????????????? ???????? ?? Telegram ??????????????")

        builder.setPositiveButton(
            "???????????????????? ????????"
        ) { _, _ ->
            try {
                lifecycleScope.launch {
                    (tService as TelegramService).initService()
                }
                subscribe<TelegramConnectedEvent>(lifecycleScope, emitRetained = true){
                    lifecycleScope.launch {
                        val tsk = async {
                            (tService as TelegramService).fillChats()
                        }
                        tsk.await()
                        try {
                            adapter.setCurrState(ClientState.ready)
                            adapter.notifyDataSetChanged()
                            pinnedAdapter.setCurrState(ClientState.ready)
                            pinnedAdapter.notifyDataSetChanged()
                            Toast.makeText(
                                baseContext,
                                "?????????????????????? ?????????????? ??????????????????",
                                Toast.LENGTH_SHORT
                            ).show()
                        }catch(e:Exception){

                        }
                    }

                }
                subscribe<StateChangedEvent>(lifecycleScope, emitRetained = true) { event ->
                    Timber.d("State changed")
                    when (event.clientState) {
                        ClientState.waitCode -> {
                            bottomSheetFragment =
                                CodeFragment()
                            (bottomSheetFragment as CodeFragment).show(
                                this.supportFragmentManager,
                                (bottomSheetFragment as CodeFragment).tag
                            )
                        }
                        ClientState.waitPassword -> {
                            bottomSheetFragment =
                                PasswordFragment()
                            (bottomSheetFragment as PasswordFragment).show(
                                this.supportFragmentManager,
                                (bottomSheetFragment as PasswordFragment).tag
                            )
                        }
                        else -> {
                            Toast.makeText(
                                baseContext,
                                event.clientState.displayName,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            catch (e: Exception){
                Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton(
            "??????"
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()

        }

        val alert = builder.create()
        alert.show()
    }

    @ExperimentalCoroutinesApi
    private fun showNotConnectedAlert(){
        val builder =
            AlertDialog.Builder(this)

        builder.setMessage("?????????? ???????????????? ?????????? ???????? ???????????????????? ?????????? ?? Telegram ??????????????")

        builder.setPositiveButton(
            "??????????"
        ) { _, _ ->
            try {
                lifecycleScope.launch {
                    (tService as TelegramService).initService()
                }
                subscribe<TelegramConnectedEvent>(lifecycleScope, emitRetained = true){
                    lifecycleScope.launch {
                        val tsk = async {
                            (tService as TelegramService).fillChats()
                        }
                        tsk.await()
                        try {
                            adapter.setCurrState(ClientState.ready)
                            adapter.notifyDataSetChanged()
                            pinnedAdapter.setCurrState(ClientState.ready)
                            pinnedAdapter.notifyDataSetChanged()
                            Toast.makeText(
                                baseContext,
                                "?????????????????????? ?????????????? ??????????????????",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        catch(e:UninitializedPropertyAccessException){

                        }
                    }

                }
                subscribe<StateChangedEvent>(lifecycleScope, emitRetained = true) { event ->
                    Timber.d("State changed")
                    when (event.clientState) {
                        ClientState.waitNumber -> {
                            bottomSheetFragment =
                                NewPhoneNumberFragment()
                            (bottomSheetFragment as NewPhoneNumberFragment).show(
                                this.supportFragmentManager,
                                (bottomSheetFragment as NewPhoneNumberFragment).tag
                            )
                        }
                        ClientState.waitCode -> {
                            bottomSheetFragment =
                                CodeFragment()
                            (bottomSheetFragment as CodeFragment).show(
                                this.supportFragmentManager,
                                (bottomSheetFragment as CodeFragment).tag
                            )
                        }
                        ClientState.waitPassword -> {
                            bottomSheetFragment =
                                PasswordFragment()
                            (bottomSheetFragment as PasswordFragment).show(
                                this.supportFragmentManager,
                                (bottomSheetFragment as PasswordFragment).tag
                            )
                        }
                        else -> {
                            Toast.makeText(
                                baseContext,
                                event.clientState.displayName,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            catch (e: Exception){
                Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton(
            "??????"
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()

        }

        val alert = builder.create()
        alert.show()
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

    private fun pinFolder(folder : FolderRealm?){
        setUpRecyclerPinned(folder)
        if(adapter.foldersFilterList.contains(folder)) {
            val index = adapter.foldersFilterList.indexOf(folder)
            folder.let { adapter.foldersFilterList.remove(it) }
            folder?.order.let { adapter.notifyItemRemoved(index) }
        }
    }

    private fun unPinFolder(folder : FolderRealm?){
        setUpRecyclerPinned(null)
        folder?.let { adapter.foldersFilterList.add(it) }
        val index = adapter.foldersFilterList.indexOf(folder)
        adapter.notifyItemInserted(index)
        folder?.order?.let { adapter.swapItems(index, it) }
        adapter.data = adapter.foldersFilterList
    }

}