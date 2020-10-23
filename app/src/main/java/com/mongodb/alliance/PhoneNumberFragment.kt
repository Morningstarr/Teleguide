package com.mongodb.alliance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.github.vardemin.materialcountrypicker.PhoneNumberEditText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.events.PhoneChangedEvent
import com.mongodb.alliance.databinding.FragmentPhoneNumberBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.PhoneNumberFragment.PhoneEditConverter.toNumber
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.api.TelegramObject
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class PhoneNumberFragment : BottomSheetDialogFragment(), GlobalBroker.Publisher, GlobalBroker.Subscriber {

    @TelegramServ
    @Inject
    lateinit var t_service : Service
    lateinit var binding: FragmentPhoneNumberBinding

    private lateinit var input : PhoneNumberEditText

    object PhoneEditConverter {

        @JvmStatic
        fun toString( //read number
            view: PhoneNumberEditText,
            value: String? //input number
        ): String? {
            return PhoneNumberEditText.fromTextNumber(view, value ?: "")
        }

        @JvmStatic
        fun toNumber( //write number
            view: PhoneNumberEditText,
            value: String? //can be ignored
        ): String? {
            return PhoneNumberEditText.toTextNumber(view)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        inflater.inflate(R.layout.fragment_phone_number, container, false)
        binding = FragmentPhoneNumberBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        input = binding.frPNInput
        var result : TelegramObject? = null
        binding.frPNConfirm.setOnClickListener {
            lifecycleScope.launch {
                try {
                    showLoading(false)
                    withContext(Dispatchers.IO) {
                        var task = async{
                            toNumber(input, "")?.let { it1 ->
                                (t_service as TelegramService).callNumberConfirm(
                                    it1
                                )
                            }
                        }
                        result = task.await()
                    }
                    if(result != null){
                        dismiss()
                        publish(PhoneChangedEvent(toNumber(input, "")))
                    }
                    else{
                        EventBus.getDefault().post(NullObjectAccessEvent("The result of request is null. Please, try again."))
                    }
                    showLoading(true)
                }
                catch (e: Exception) {
                    timber.log.Timber.e(e.message)
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT)
                        .show()
                        showLoading(true)
                }
            }
        }
    }

    fun showLoading(show : Boolean){
        binding.frPNConfirm.isEnabled = show
        if(show) {
            binding.frPNProgress.visibility = View.GONE
        }
        else{
            binding.frPNProgress.visibility = View.VISIBLE
        }
    }

}