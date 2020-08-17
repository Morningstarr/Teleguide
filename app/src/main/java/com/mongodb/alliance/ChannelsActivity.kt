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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.databinding.ActivityMainBinding
import com.mongodb.alliance.databinding.FragmentPasswordBinding
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.ChannelAdapter
import dev.whyoleg.ktd.api.TdApi
import dev.whyoleg.ktd.api.TelegramObject
import io.realm.Realm
import io.realm.internal.common.Dispatcher
import io.realm.kotlin.where
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime

@ExperimentalTime
class ChannelsActivity : AppCompatActivity() {
    private lateinit var realm: Realm
    private var user: User? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChannelAdapter
    private lateinit var fab: FloatingActionButton
    private lateinit var chatList : TdApi.ChatList

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            /*val config = SyncConfiguration.Builder(user!!, "New Folder")
                .waitForInitialRemoteData()
                .build()

            Realm.setDefaultConfiguration(config)*/

            lifecycleScope.launch{
                withContext(Dispatchers.IO){
                    //getChats()
                    //deferredOne.await()
                }

                /*for(TdApi.Chat in chatList){
                    var newChannel = ChannelRealm(TdApi.Chat)
                }*/
                Toast.makeText(baseContext, "11", Toast.LENGTH_SHORT).show()
            }

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


    /*suspend fun getChats(): TelegramObject {
        return client.exec(TdApi.GetChats(chatList, 0, 0, 0))
    }*/
}