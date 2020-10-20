package com.mongodb.alliance.ui

import android.app.ActionBar
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import com.daimajia.swipe.SwipeLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.R
import com.mongodb.alliance.adapters.FolderAdapter
import com.mongodb.alliance.adapters.SimpleItemTouchHelperCallback
import com.mongodb.alliance.channelApp
import com.mongodb.alliance.databinding.ActivityFolderBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.events.OpenFolderEvent
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
    private lateinit var adapter: FolderAdapter
    private lateinit var fab: FloatingActionButton
    private lateinit var customActionBarView : View
    private lateinit var rootLayout : CoordinatorLayout

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        subscribe<OpenFolderEvent>(lifecycleScope){ event ->
            val intent = Intent(baseContext, ChannelsRealmActivity::class.java)
            intent.putExtra("folderId", event.folderId)
            startActivity(intent)
        }

        EventBus.getDefault().register(this)

        val actionbar = supportActionBar
        if (actionbar != null) {
            actionbar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            actionbar.setDisplayShowCustomEnabled(true)
            actionbar.setCustomView(R.layout.action_bar_drawable)
            customActionBarView = actionbar.customView
            customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_back).visibility = View.GONE
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
        }

        customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_menu).setOnClickListener {
            rootLayout = binding.coordinatorFolder
            var anchor = binding.anchorFolders
            val wrapper = ContextThemeWrapper(this,
                R.style.MyPopupMenu
            )
            val popup = PopupMenu(wrapper, anchor, Gravity.END)

            try {
                val fields: Array<Field> = popup.javaClass.declaredFields
                for (field in fields) {
                    if ("mPopup" == field.getName()) {
                        field.setAccessible(true)
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
                when(item.itemId){
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
                                startActivity(Intent(baseContext, LoginActivity::class.java))
                            }
                        }
                        catch(e: Exception){
                            Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                true
            }
        }

        binding = ActivityFolderBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        realm = Realm.getDefaultInstance()
        recyclerView = binding.foldersList
        fab = binding.fldrFab

        fab.setOnClickListener {
            val input = EditText(this)
            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setMessage("Enter folder name:")
                .setCancelable(true)
                .setPositiveButton("Add") { dialog, _ -> run {
                    dialog.dismiss()
                    try {
                        val name = input.text.toString()
                        val partition = user?.id ?: "" // FIXME: show error if nil
                        val folder = FolderRealm(
                            name,
                            partition
                        )
                        realm.executeTransactionAsync { realm ->
                            realm.insert(folder)
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
            dialog.setTitle("Add New Folder")
            dialog.show()
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
                    override fun onSuccess(realm: Realm) {
                        // since this realm should live exactly as long as this activity, assign the realm to a member variable
                            this@FolderActivity.realm = realm
                            setUpRecyclerView(realm)
                            showLoading(false)
                        }
                    })
            } catch (e: Exception) {
                Timber.e(e.message)
            }
        }
    }


    private fun setUpRecyclerView(realm: Realm) {
        adapter = FolderAdapter(
            realm.where<FolderRealm>().sort("_id")
                .findAll().toMutableList()
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        //recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        val callback: ItemTouchHelper.Callback = SimpleItemTouchHelperCallback(adapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerView.adapter = null
        realm.close()
    }

    /*override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.action_refresh).isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
                        startActivity(Intent(baseContext, LoginActivity::class.java))
                    }
                }
                catch(e: Exception){
                    Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_connect_telegram -> {
                lateinit var state : ClientState
                startActivity(Intent(baseContext, ConnectTelegramActivity::class.java))
                true
            }
            R.id.action_profile -> {
                startActivity(Intent(baseContext, ProfileActivity::class.java))
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }*/

    private fun showLoading(show : Boolean){
        if(!show) {
            binding.foldersProgress.visibility = View.GONE
        }
        else{
            binding.foldersProgress.visibility = View.VISIBLE
        }
    }

}