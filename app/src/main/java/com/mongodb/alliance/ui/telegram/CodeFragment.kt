package com.mongodb.alliance.ui.telegram

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.R
import com.mongodb.alliance.databinding.FragmentCodeBinding
import com.mongodb.alliance.di.TelegramServ
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
class CodeFragment : BottomSheetDialogFragment() {

    @TelegramServ
    @Inject lateinit var t_service : Service

    private var _binding: FragmentCodeBinding? = null
    private val binding get() = _binding!!

    private lateinit var input : EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        inflater.inflate(R.layout.fragment_code, container, false)
        _binding = FragmentCodeBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        input = binding.frCdInput
        lateinit var result : TelegramObject;
        binding.frCdConfirm.setOnClickListener {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        //var result = callCodeConfirm()
                        (t_service as TelegramService).callCodeConfirm(input.text.toString())
                    }
                    dismiss()
                } catch (e: Exception) {
                    timber.log.Timber.e(e.message)
                    Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private suspend fun callCodeConfirm(): TelegramObject {
        return (t_service.returnServiceObj() as TelegramClient).exec(TdApi.CheckAuthenticationCode(input.text.toString()))
    }

}
