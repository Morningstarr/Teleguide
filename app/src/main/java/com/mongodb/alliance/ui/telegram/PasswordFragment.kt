package com.mongodb.alliance.ui.telegram

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.removeRetained
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.databinding.NewFragmentPasswordBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.*
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import dagger.hilt.android.AndroidEntryPoint
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
    private lateinit var binding: NewFragmentPasswordBinding

    private lateinit var input : EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        binding = NewFragmentPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        input = binding.telePassEdit
        var result : TelegramObject? = null
        binding.passConfirmBtn.setOnClickListener {
            lifecycleScope.launch {
                try {
                    showLoading(false)
                    withContext(Dispatchers.IO) {
                        val task = async {
                            (t_service as TelegramService).callPasswordConfirm(input.text.toString())
                        }
                        result = task.await()
                    }
                    if(result != null){
                        dismiss()
                        removeRetained<StateChangedEvent>()
                    }
                    else{
                        Toast.makeText(activity, "Bad request. Check your entering data and internet connection and try again", Toast.LENGTH_SHORT).show()
                        showLoading(true)
                        cancel()
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e)
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT)
                        .show()
                    showLoading(true)
                }
            }
        }
    }

    fun showLoading(show : Boolean){
        binding.passConfirmBtn.isEnabled = show
        binding.telePassEdit.isEnabled = show
        if(show){
            binding.shadow.visibility = View.VISIBLE
        }
        else{
            binding.shadow.visibility = View.INVISIBLE
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        removeRetained<StateChangedEvent>()
        EventBus.getDefault().removeStickyEvent(PasswordStickyEvent())
    }

}
