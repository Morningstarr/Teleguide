package com.mongodb.alliance.ui

import android.R.attr.password
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.channelApp
import com.mongodb.alliance.databinding.FragmentAddFolderBinding
import com.mongodb.alliance.databinding.FragmentEditFolderBinding
import com.mongodb.alliance.events.FolderEditEvent
import com.mongodb.alliance.events.FolderPinEvent
import com.mongodb.alliance.events.FolderUnpinEvent
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.util.regex.Pattern
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime


class EditFolderFragment(var folder : FolderRealm) : BottomSheetDialogFragment(), CoroutineScope, GlobalBroker.Publisher {
    private lateinit var binding : FragmentEditFolderBinding

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        binding = FragmentEditFolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @InternalCoroutinesApi
    @ExperimentalTime
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nameEdit = binding.enterNewFolderName

        binding.btnConfirmEdit.setOnClickListener{
            loading(false)
            try {
                validateFolderName(nameEdit.text.toString())
                val folder = updateFolder(nameEdit.text.toString())
                publish(FolderEditEvent(folder))
                //EventBus.getDefault().post(FolderUnpinEvent("", null))
                dismiss()
            }
            catch(e:Exception){
                Timber.e(e.message)
                Toast.makeText(activity, e.message, Toast.LENGTH_LONG).show()
                loading(true)
            }

        }

        nameEdit.hint = folder.name

    }

    @InternalCoroutinesApi
    @ExperimentalTime
    @RequiresApi(Build.VERSION_CODES.N)
    private fun updateFolder(name: String) : FolderRealm? {
        val bgRealm = Realm.getDefaultInstance()
        var tempFolder : FolderRealm? = null
        runBlocking {
            tempFolder = bgRealm.where<FolderRealm>().equalTo("_id", folder._id)
                .findFirst() as FolderRealm
            bgRealm.executeTransaction {
                tempFolder?.name = name
            }
        }

        bgRealm.close()
        return tempFolder
    }

    private fun validateFolderName(name: String){
        if(name.length < 2 || name.length > 16){
            throw (Exception("Имя папки должно быть длиной 2-16 символов!"))
        }

        val special = Pattern.compile("[!@#$%&*(),.+=|<>?{}\\[\\]~]")

        val m = special.matcher(name)
        val b = m.find()

        if (b) {
            throw(Exception("Название папки может содержать только цифры, буквы и символы (-, _) !"))
        }
    }

    private fun loading(show : Boolean){
        binding.btnConfirmEdit.isEnabled = show
        binding.enterNewFolderName.isEnabled = show
        if(show) {
            binding.shadow.visibility = View.VISIBLE
        }
        else{
            binding.shadow.visibility = View.INVISIBLE
        }
    }
}