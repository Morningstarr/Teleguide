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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mongodb.alliance.model.FolderAdapter
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import io.realm.kotlin.where
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.InternalCoroutinesApi
import timber.log.Timber
import kotlin.time.ExperimentalTime


class FolderActivity : AppCompatActivity() {
    private lateinit var realm: Realm
    private var user: User? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FolderAdapter
    private lateinit var fab: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder)

        val actionbar = supportActionBar
        actionbar!!.title = "My folders"
        actionbar.setDisplayHomeAsUpEnabled(true)
        actionbar.setDisplayHomeAsUpEnabled(true)

        realm = Realm.getDefaultInstance()
        recyclerView = findViewById(R.id.folders_list)
        fab = findViewById(R.id.floating_action_button2)

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
        try{
            user = channelApp.currentUser()

            val config = SyncConfiguration.Builder(user!!, user!!.id)
                .waitForInitialRemoteData()
                .build()

            Realm.setDefaultConfiguration(config)

            //исключение вылетает здесь
            Realm.getInstanceAsync(config, object: Realm.Callback() {
                override fun onSuccess(realm: Realm) {
                    // since this realm should live exactly as long as this activity, assign the realm to a member variable
                    this@FolderActivity.realm = realm
                    setUpRecyclerView(realm)
                }
            })
        }
        catch(e: Exception){
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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

}