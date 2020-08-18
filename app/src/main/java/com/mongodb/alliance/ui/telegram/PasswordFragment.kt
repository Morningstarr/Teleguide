package com.mongodb.alliance.ui.telegram

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.R
import com.mongodb.alliance.databinding.FragmentCodeBinding
import com.mongodb.alliance.databinding.FragmentPasswordBinding
import com.mongodb.alliance.databinding.FragmentPhoneNumberBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.services.telegram.Service
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
class PasswordFragment : BottomSheetDialogFragment() {

    @TelegramServ
    @Inject
    lateinit var t_service : Service
    private var _binding: FragmentPasswordBinding? = null
    private val binding get() = _binding!!

    private lateinit var input : EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        inflater.inflate(R.layout.fragment_password, container, false)
        _binding = FragmentPasswordBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        input = binding.frPassInput
        lateinit var result : TelegramObject;
        binding.frPassConfirm.setOnClickListener {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        var result = callPasswordConfirm()
                    }
                    if (result.toString().contains("Ok")) {
                        dismiss()
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e.message)
                    Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private suspend fun callPasswordConfirm(): TelegramObject {
        return (t_service.returnServiceObj() as TelegramClient).exec(TdApi.CheckAuthenticationPassword(input.text.toString()))
    }

}
