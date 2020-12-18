package com.mongodb.alliance.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.channelApp
import com.mongodb.alliance.databinding.FragmentAddFolderBinding
import com.mongodb.alliance.events.AddFolderEvent
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.InternalCoroutinesApi
import timber.log.Timber
import java.util.regex.Pattern
import kotlin.time.ExperimentalTime


class AddFolderFragment : BottomSheetDialogFragment(), GlobalBroker.Publisher {
    private lateinit var binding : FragmentAddFolderBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        binding = FragmentAddFolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @InternalCoroutinesApi
    @ExperimentalTime
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nameEdit = binding.enterFolderName

        binding.btnConfirmName.setOnClickListener{
            loading(false)
            try {
                validateFolderName(nameEdit.text.toString())
                val folder = createFolder(nameEdit.text.toString())
                if(folder != null) {
                    publish(AddFolderEvent(folder))
                }
                dismiss()
            }
            catch(e:Exception){
                Timber.e(e)
                Toast.makeText(activity, e.message, Toast.LENGTH_LONG).show()
                loading(true)
            }

        }

    }

    @InternalCoroutinesApi
    @ExperimentalTime
    @RequiresApi(Build.VERSION_CODES.N)
    private fun createFolder(name: String) : FolderRealm?{
        val bgRealm = Realm.getDefaultInstance()
        var folder : FolderRealm? = null
        try {
            val partition = channelApp.currentUser()?.id ?: ""
            folder = FolderRealm(
                name,
                partition
            )
            folder.isPinned = false
            val order = bgRealm.where<FolderRealm>().max("order")
            if (order != null) {
                folder.order = order.toInt() + 1
            }
            bgRealm.executeTransaction { realm ->
                realm.insert(folder)
            }
        }
        catch(e:Exception){
            Toast.makeText(this.activity, e.message, Toast.LENGTH_SHORT).show()
        }
        finally{
            bgRealm.close()
            return folder
        }
    }

    private fun validateFolderName(name: String){
        if(name.length < 2 || name.length > 25){
            throw (Exception("Имя папки должно быть длиной 2-25 символов!"))
        }

        val special = Pattern.compile("[!@#$%&*(),.+=|<>?{}\\[\\]~]")

        val m = special.matcher(name)
        val b = m.find()

        if (b) {
            throw(Exception("Название папки может содержать только цифры, буквы и символы (-, _) !"))
        }
    }

    private fun loading(show : Boolean){
        binding.btnConfirmName.isEnabled = show
        binding.enterFolderName.isEnabled = show
        if(show) {
            binding.shadow.visibility = View.VISIBLE
        }
        else{
            binding.shadow.visibility = View.INVISIBLE
        }
    }
}