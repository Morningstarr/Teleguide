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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.channelApp
import com.mongodb.alliance.databinding.FragmentAddFolderBinding
import com.mongodb.alliance.events.AddFolderEvent
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.util.regex.Pattern
import kotlin.time.ExperimentalTime


class AddFolderFragment : BottomSheetDialogFragment() {
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
                createFolder(nameEdit.text.toString())
                EventBus.getDefault().post(AddFolderEvent())
                dismiss()
            }
            catch(e:Exception){
                Timber.e(e.message)
                Toast.makeText(activity, e.message, Toast.LENGTH_LONG).show()
                loading(true)
            }

        }

    }

    @InternalCoroutinesApi
    @ExperimentalTime
    @RequiresApi(Build.VERSION_CODES.N)
    private fun createFolder(name: String){
        val bgRealm = Realm.getDefaultInstance()

        val partition = channelApp.currentUser()?.id ?: "" // FIXME: show error if nil
        val folder = FolderRealm(
            name,
            partition
        )
        folder.isPinned = false
        val order = bgRealm.where<FolderRealm>().max("order")
        if (order != null) {
            folder.order = order.toInt() + 1
        }
        //lifecycleScope.launch {
            //val task = async {
                bgRealm.executeTransaction { realm ->
                    realm.insert(folder)
                }
            //}
            //task.await()
            //(activity as FolderActivity).setUpRecyclerView(bgRealm)
        //}

        bgRealm.close()
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