package com.mongodb.alliance

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import com.mongodb.alliance.databinding.ActivityFolderBinding
import com.mongodb.alliance.databinding.ActivityMainBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.model.FolderAdapter
import com.mongodb.alliance.model.FolderRealm
import com.mongodb.alliance.model.OpenFolderEvent
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.telegram.ConnectTelegramActivity
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.kotlin.where
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.*
import timber.log.Timber
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

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var binding: ActivityFolderBinding

    @TelegramServ
    @Inject
    lateinit var t_service: Service

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        subscribe<OpenFolderEvent>(lifecycleScope){ event ->
            val intent = Intent(baseContext, ChannelsActivity::class.java)
            intent.putExtra("folderName", event.folderName)
            startActivity(intent)
        }

        val actionbar = supportActionBar
        actionbar!!.title = "My folders"

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
            try {
                user = channelApp.currentUser()
            } catch (e: IllegalStateException) {
                Timber.e(e.message)
            }
            if (user == null) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {

                val config = SyncConfiguration.Builder(user!!, user!!.id)
                    .waitForInitialRemoteData()
                    .build()

                Realm.setDefaultConfiguration(config)

                try {
                    Realm.getInstanceAsync(config, object : Realm.Callback() {
                        override fun onSuccess(realm: Realm) {
                            // since this realm should live exactly as long as this activity, assign the realm to a member variable
                            this@FolderActivity.realm = realm
                            setUpRecyclerView(realm)
                        }
                    })
                } catch (e: Exception) {
                    Timber.e(e.message)
                }
            }
        }
        catch(e:Exception){
            Timber.e(e.message)
        }

    }

    private fun setUpRecyclerView(realm: Realm) {
        adapter = FolderAdapter(
            realm.where<FolderRealm>().sort("_id")
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
        //unsubscribe()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.action_refresh).isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                lifecycleScope.launch{
                    binding.foldersProgress.visibility = View.VISIBLE

                    user?.logOutAsync {
                        if (it.isSuccess) {
                            realm = Realm.getDefaultInstance()

                            //clear local data
                            realm.beginTransaction();
                            realm.deleteAll();
                            realm.commitTransaction();

                            realm.close()

                            user = null
                        } else {
                            Timber.e("log out failed! Error: ${it.error}")
                            return@logOutAsync
                        }
                    }

                    withContext(Dispatchers.IO) {
                        (t_service as TelegramService).logOut()
                    }

                    Timber.d("user logged out")

                    binding.foldersProgress.visibility = View.GONE
                    startActivity(Intent(baseContext, LoginActivity::class.java))
                }
                true
            }
            R.id.action_connect_telegram -> {
                lateinit var state : ClientState
                launch {
                    val task = async {
                        withContext(Dispatchers.IO) {
                            (t_service as TelegramService).returnClientState()
                        }
                    }
                    state = task.await()

                    if(state != ClientState.ready) {
                        startActivity(Intent(baseContext, ConnectTelegramActivity::class.java))
                    }
                    else{
                        Toast.makeText(baseContext, "Account already connected", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

}