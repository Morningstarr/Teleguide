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
import com.mongodb.alliance.databinding.FragmentPhoneNumberBinding
import dev.whyoleg.ktd.api.TdApi
import dev.whyoleg.ktd.api.TelegramObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime

@InternalCoroutinesApi
@ExperimentalTime
class PhoneNumberFragment : BaseBottomSheetFragment() {

    //private var _binding: FragmentPhoneNumberBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    //private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        return inflater.inflate(R.layout.fragment_phone_number, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        input = view.findViewById<EditText>(R.id.fr_p_n_input)
        lateinit var result : TelegramObject;
        view.findViewById<Button>(R.id.fr_p_n_confirm).setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    var result = callNumberConfirm()
                    if(result.toString().contains("Ok")) {
                        dismiss()
                    }
                }
            }
        }
    }

    /*override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }*/

    private suspend fun callNumberConfirm(): TelegramObject {
        return (activity as ConnectTelegramActivity).client.exec(TdApi.SetAuthenticationPhoneNumber(input.text.toString(),null))
    }

}