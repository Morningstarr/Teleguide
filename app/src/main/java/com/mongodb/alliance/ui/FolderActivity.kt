package com.mongodb.alliance.ui

import android.app.ActionBar
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
class FolderActivity : AppCompatActivity(), GlobalBroker.Subscriber, CoroutineScope {
    private lateinit var realm: Realm
    private var user: User? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var pinnedRecyclerView: RecyclerView
    private lateinit var adapter: FolderAdapter
    private lateinit var pinnedAdapter: PinnedFolderAdapter
    private lateinit var fab: FloatingActionButton
    private lateinit var customActionBarView : View
    private lateinit var rootLayout : CoordinatorLayout
    private var isSelecting : Boolean = false

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var binding: ActivityFolderBinding

    @TelegramServ
    @Inject
    lateinit var t_service: Service

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: NullObjectAccessEvent) {
        Toast.makeText(baseContext, event.message, Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: SelectFolderEvent) {
        val actionbar = supportActionBar
        isSelecting = true
        if (actionbar != null) {
            if(actionbar.customView.findViewById<TextView>(R.id.actionBar_folders_count) == null) {
                actionbar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
                actionbar.setDisplayShowCustomEnabled(true)
                actionbar.setCustomView(R.layout.action_bar_options_drawable)
                customActionBarView = actionbar.customView
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
                }
                else {
                    countText.text = (countText.text.toString().toInt() - 1).toString()
                }
            }

            customActionBarView.findViewById<ImageView>(R.id.actionBar_button_cancel).setOnClickListener {
                setDefaultActionBar()
                isSelecting = false
                setUpRecyclerView(realm)
            }

            customActionBarView.findViewById<ImageView>(R.id.actionBar_button_delete).setOnClickListener {
                //todo delete all selected folders
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: FolderPinDenyEvent) {
        val builder =
            AlertDialog.Builder(this)

        builder.setMessage("Нельзя закрепить более одного чата.")

        builder.setPositiveButton(
            "Открепить чат"
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

    @RequiresApi(Build.VERSION_CODES.N)
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: FolderPinEvent){
        setUpRecyclerPinned(event.pinnedFolder)
        refreshRecyclerView()
    }

    @RequiresApi(Build.VERSION_CODES.N)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        subscribe<OpenFolderEvent>(lifecycleScope){ event ->
            val intent = Intent(baseContext, ChannelsRealmActivity::class.java)
            intent.putExtra("folderId", event.folderId)
            startActivity(intent)
        }

        EventBus.getDefault().register(this)

        setDefaultActionBar()

        binding = ActivityFolderBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        realm = Realm.getDefaultInstance()
        recyclerView = binding.foldersList
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

    override fun onStart() {
        super.onStart()

        try {
            user = channelApp.currentUser()
        } catch (e: IllegalStateException) {
            Timber.e(e.message)
        }
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            lifecycleScope.launch {
                showLoading(true)
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
            }

            val config = SyncConfiguration.Builder(user, user?.id)
                .waitForInitialRemoteData()
                .build()

            Realm.setDefaultConfiguration(config)

            try {
                Realm.getInstanceAsync(config, object : Realm.Callback() {
                    @RequiresApi(Build.VERSION_CODES.N)
                    override fun onSuccess(realm: Realm) {
                        // since this realm should live exactly as long as this activity, assign the realm to a member variable
                            this@FolderActivity.realm = realm
                            setUpRecyclerView(realm)
                            setUpRecyclerPinned(null)
                            showLoading(false)
                        }
                    })
            } catch (e: Exception) {
                Timber.e(e.message)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun setUpRecyclerView(realm: Realm) {
        val mutableFolders = realm.where<FolderRealm>().sort("order")
            .findAll().toMutableList()
        mutableFolders.removeIf {
            it.isPinned
        }

        adapter = FolderAdapter(
            mutableFolders
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        val callback: ItemTouchHelper.Callback = SimpleItemTouchHelperCallback(adapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recyclerView)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    open fun refreshRecyclerView(){
        val mutableFolders = realm.where<FolderRealm>().sort("order")
            .findAll().toMutableList()
        mutableFolders.removeIf {
            it.isPinned
        }
        adapter.setDataList(mutableFolders)
    }

    private fun setUpRecyclerPinned(pinned: FolderRealm?){
        val found : FolderRealm?
        if(pinned == null){
            realm = Realm.getDefaultInstance()
            found = realm.where<FolderRealm>().equalTo("isPinned", true).findFirst()
            if(found != null){
                pinnedAdapter = PinnedFolderAdapter(found)
                pinnedRecyclerView.layoutManager = LinearLayoutManager(this)
                pinnedRecyclerView.adapter = pinnedAdapter
                pinnedRecyclerView.setHasFixedSize(true)
                pinnedRecyclerView.visibility = View.VISIBLE
                val param = recyclerView.layoutParams as ViewGroup.MarginLayoutParams
                param.setMargins(0,200,0,0)
                recyclerView.layoutParams = param
            }
            else{
                pinnedRecyclerView.adapter = null
                pinnedRecyclerView.visibility = View.GONE
                val param = recyclerView.layoutParams as ViewGroup.MarginLayoutParams
                param.setMargins(0,0,0,0)
                recyclerView.layoutParams = param
            }
        }
        else {
            pinnedAdapter = PinnedFolderAdapter(pinned)
            pinnedRecyclerView.layoutManager = LinearLayoutManager(this)
            pinnedRecyclerView.adapter = pinnedAdapter
            pinnedRecyclerView.setHasFixedSize(true)
            pinnedRecyclerView.visibility = View.VISIBLE
            val param = recyclerView.layoutParams as ViewGroup.MarginLayoutParams
            param.setMargins(0,200,0,0)
            recyclerView.layoutParams = param
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerView.adapter = null
        realm.close()
    }

    private fun showLoading(show : Boolean){
        if(!show) {
            binding.foldersProgress.visibility = View.GONE
        }
        else{
            binding.foldersProgress.visibility = View.VISIBLE
        }
    }

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
            nameText.text = "TeleGuide"
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

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBackPressed() {
        //super.onBackPressed()
        if(isSelecting) {
            setDefaultActionBar()
            isSelecting = false
            setUpRecyclerView(realm)
        }
        else{
            super.onBackPressed()
        }
    }

}