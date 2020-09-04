package com.mongodb.alliance

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.mongodb.alliance.databinding.ActivityFolderBinding
import com.mongodb.alliance.databinding.ActivityProfileBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.model.UserRealm
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.kotlin.where
import io.realm.mongodb.User
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class ProfileActivity : AppCompatActivity() {

    @TelegramServ
    @Inject
    lateinit var t_service: Service
    private lateinit var binding: ActivityProfileBinding
    private lateinit var realm: Realm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actionbar = supportActionBar
        actionbar!!.title = "My profile"
        actionbar.setDisplayHomeAsUpEnabled(true)
        actionbar.setDisplayHomeAsUpEnabled(true)

        realm = Realm.getDefaultInstance()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)


        val currUser = realm.where<UserRealm>().equalTo("user_id", channelApp.currentUser()?.id).findFirst()
        binding.profEmail.text = currUser?.name.toString()
        //val urs = realm.where<UserRealm>().findAll()

        lifecycleScope.launch {
            val task = async {
                withContext(Dispatchers.IO) {
                    (t_service as TelegramService).returnClientState()
                }
            }
            val state = task.await()
            when (state) {
                ClientState.ready -> {
                    val taskNumber = async {
                        (t_service as TelegramService).getPhoneNumber()
                    }
                    val number = taskNumber.await()
                    binding.profNumber.text = number

                    val taskName = async {
                        (t_service as TelegramService).getProfileName()
                    }
                    val username = taskName.await()
                    binding.profTgAccount.text = username
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}