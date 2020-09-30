package com.mongodb.alliance.ui.telegram

import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import cafe.adriel.broker.removeRetained
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.R
import com.mongodb.alliance.databinding.FragmentCodeBinding
import com.mongodb.alliance.databinding.FragmentPasswordBinding
import com.mongodb.alliance.databinding.FragmentPhoneNumberBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.events.StateChangedEvent
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.TelegramClient
import dev.whyoleg.ktd.api.TdApi
import dev.whyoleg.ktd.api.TelegramObject
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class PasswordFragment : BottomSheetDialogFragment(), GlobalBroker.Subscriber, GlobalBroker.Publisher {

    @TelegramServ
    @Inject
    lateinit var t_service : Service
    private lateinit var binding: FragmentPasswordBinding

    private lateinit var input : EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        var view = inflater.inflate(R.layout.new_fragment_password, container, false)
        //binding = FragmentPasswordBinding.inflate(inflater, container, false)
        //return binding.root
        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /*input = binding.frPassInput
        var result : TelegramObject? = null
        binding.frPassConfirm.setOnClickListener {
            lifecycleScope.launch {
                try {
                    showLoading(false)
                    withContext(Dispatchers.IO) {
                        var task = async {
                            (t_service as TelegramService).callPasswordConfirm(input.text.toString())
                        }
                        result = task.await()
                    }
                    if(result != null){
                        dismiss()
                        removeRetained<StateChangedEvent>()
                    }
                    else{
                        EventBus.getDefault().post(NullObjectAccessEvent("The result of request is null. Please, try again."))
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e.message)
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT)
                        .show()
                    showLoading(true)
                }
            }
        }*/
    }

    fun showLoading(show : Boolean){
        binding.frPassConfirm.isEnabled = show
        if(show) {
            binding.frPassProgress.visibility = View.GONE
        }
        else{
            binding.frPassProgress.visibility = View.VISIBLE
        }
    }

}
