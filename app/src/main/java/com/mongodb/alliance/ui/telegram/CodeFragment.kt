package com.mongodb.alliance.ui.telegram

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
import com.mongodb.alliance.databinding.FragmentCodeBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.StateChangedEvent
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.TelegramClient
import dev.whyoleg.ktd.api.TdApi
import dev.whyoleg.ktd.api.TelegramObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class CodeFragment() : BottomSheetDialogFragment(), GlobalBroker.Subscriber {

    @TelegramServ
    @Inject lateinit var t_service : Service

    private lateinit var binding: FragmentCodeBinding

    private lateinit var n1 : EditText
    private lateinit var n2 : EditText
    private lateinit var n3 : EditText
    private lateinit var n4 : EditText
    private lateinit var n5 : EditText


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        inflater.inflate(R.layout.fragment_code, container, false)
        binding = FragmentCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        n1 = binding.frCdN1
        n1.requestFocus()
        n2 = binding.frCdN2
        n3 = binding.frCdN3
        n4 = binding.frCdN4
        n5 = binding.frCdN5

        n1.addTextChangedListener(object : CodeTextWatcher(n1, n2, true) { })
        n2.addTextChangedListener(object : CodeTextWatcher(n1, n3, true) { })
        n3.addTextChangedListener(object : CodeTextWatcher(n2, n4,true) { })
        n4.addTextChangedListener(object : CodeTextWatcher(n3, n5,true) { })
        n5.addTextChangedListener(object : CodeTextWatcher(n4, binding.frCdConfirm, false) { })


        lateinit var result : TelegramObject;
        binding.frCdConfirm.setOnClickListener {
            var input : String = n1.text.toString() + n2.text.toString() +
                    n3.text.toString() + n4.text.toString() + n5.text.toString()
            lifecycleScope.launch {
                showLoading(false)
                try {
                    withContext(Dispatchers.IO) {
                        //var result = callCodeConfirm()
                        (t_service as TelegramService).callCodeConfirm(input)
                    }
                    showLoading(true)
                    dismiss()
                    removeRetained<StateChangedEvent>()
                } catch (e: Exception) {
                    timber.log.Timber.e(e.message)
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT)
                        .show()
                    showLoading(true)
                }
            }
        }
    }

    fun showLoading(show : Boolean){
        binding.frCdConfirm.isEnabled = show
        if(show) {
            binding.frCdProgress.visibility = View.GONE
        }
        else{
            binding.frCdProgress.visibility = View.VISIBLE
        }
    }

}
