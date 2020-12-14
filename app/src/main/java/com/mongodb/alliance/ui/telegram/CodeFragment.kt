package com.mongodb.alliance.ui.telegram

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.removeRetained
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.CodeTextWatcher
import com.mongodb.alliance.R
import com.mongodb.alliance.databinding.NewFragmentCodeBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.events.StateChangedEvent
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
class CodeFragment() : BottomSheetDialogFragment(), GlobalBroker.Subscriber {

    @TelegramServ
    @Inject lateinit var t_service : Service

    private lateinit var binding: NewFragmentCodeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        binding = NewFragmentCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var result : TelegramObject? = null
        binding.codeConfirmBtn.setOnClickListener {
            var input : String = binding.teleCodeEdit.text.toString()
            lifecycleScope.launch {
                showLoading(false)
                try {
                    withContext(Dispatchers.IO) {
                        val task = async {
                            (t_service as TelegramService).callCodeConfirm(input)
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
                    showLoading(true)

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
        binding.codeConfirmBtn.isEnabled = show
        binding.teleCodeEdit.isEnabled = show
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
    }

}
