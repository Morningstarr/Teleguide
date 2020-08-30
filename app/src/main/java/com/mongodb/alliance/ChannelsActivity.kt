package com.mongodb.alliance

import android.content.Intent
import android.opengl.Visibility
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.databinding.ActivityChannelsBinding
import com.mongodb.alliance.databinding.ActivityFolderBinding
import com.mongodb.alliance.databinding.ActivityMainBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.model.ChannelAdapter
import com.mongodb.alliance.model.FolderAdapter
import com.mongodb.alliance.model.FolderRealm
import com.mongodb.alliance.model.OpenChannelEvent
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.kotlin.where
import io.realm.mongodb.User
import kotlinx.coroutines.*
import org.bson.types.ObjectId
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@ExperimentalTime
@InternalCoroutinesApi
@AndroidEntryPoint
class ChannelsActivity : AppCompatActivity(), GlobalBroker.Subscriber {
    private var realm: Realm = Realm.getDefaultInstance()
    private var user: User? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChannelAdapter
    private lateinit var fab: FloatingActionButton

    private lateinit var binding: ActivityChannelsBinding

    @TelegramServ
    @Inject
    lateinit var t_service: Service

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val folderId = intent.getStringExtra("folderId")
        val folder = realm.where<FolderRealm>().equalTo("_id", ObjectId(folderId)).findFirst()
        val actionbar = supportActionBar
        actionbar?.title = folder?.name
        actionbar?.setDisplayHomeAsUpEnabled(true)
        actionbar?.setDisplayHomeAsUpEnabled(true)

        binding = ActivityChannelsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        fab = binding.channelsFab

        fab.setOnClickListener {
            var flag = false
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
                    val intent = Intent(baseContext, ChannelsListActivity::class.java)
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

    }

    private fun showLoading(show : Boolean){
        if (show) {
            binding.channelsInFolderProgress.visibility = View.VISIBLE }
        else{
            binding.channelsInFolderProgress.visibility = View.GONE
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}