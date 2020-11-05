package com.mongodb.alliance.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.ActionBar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.R
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.model.*
import com.mongodb.alliance.adapters.ChannelRealmAdapter
import com.mongodb.alliance.channelApp
import com.mongodb.alliance.databinding.ActivityChannelsRealmBinding
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.events.OpenChannelEvent
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
class ChannelsRealmActivity : AppCompatActivity(), GlobalBroker.Subscriber {
    private var realm: Realm = Realm.getDefaultInstance()
    private var user: User? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChannelRealmAdapter
    private lateinit var fab: FloatingActionButton
    private var folder: FolderRealm? = null
    private lateinit var binding: ActivityChannelsRealmBinding
    private var folderId : String? = null
    private lateinit var customActionBarView : View
    private lateinit var rootLayout : CoordinatorLayout

    @TelegramServ
    @Inject
    lateinit var t_service: Service

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: NullObjectAccessEvent) {
        Toast.makeText(baseContext, event.message, Toast.LENGTH_SHORT).show()
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

        subscribe<OpenChannelEvent>(lifecycleScope){ event ->
            startActivity(event.intent)
        }
        EventBus.getDefault().register(this)

        setDefaultActionBar()
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

            val config = SyncConfiguration.Builder(user, user?.id)
                .waitForInitialRemoteData()
                .build()

            Realm.setDefaultConfiguration(config)

            try {
                Realm.getInstanceAsync(config, object: Realm.Callback() {
                    override fun onSuccess(realm: Realm) {
                        // since this realm should live exactly as long as this activity, assign the realm to a member variable
                        this@ChannelsRealmActivity.realm = realm
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
        val query = realm.where<ChannelRealm>().equalTo("folder._id", ObjectId(folderId)).findAll().sort("_id")
        if(query.size != 0) {
            binding.textLayout.visibility = View.INVISIBLE
            adapter = ChannelRealmAdapter(
                query
            )
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
            recyclerView.setHasFixedSize(true)
            recyclerView.addItemDecoration(
                DividerItemDecoration(
                    this,
                    DividerItemDecoration.VERTICAL
                )
            )
        }
        else{
            binding.textLayout.visibility = View.VISIBLE
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
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

            /*val params = LinearLayout.LayoutParams(
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
            nameText.textSize = 24F*/

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