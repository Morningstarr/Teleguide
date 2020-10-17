package com.mongodb.alliance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputLayout
import com.mongodb.alliance.databinding.FragmentPhoneNumberBinding
import com.mongodb.alliance.databinding.FragmentSignUpBinding
import com.mongodb.alliance.databinding.NewFragmentPhoneNumberBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.events.PhoneChangedEvent
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.telegram.PhoneNumberFragment
import com.mukesh.countrypicker.Country
import com.mukesh.countrypicker.CountryPicker
import com.mukesh.countrypicker.listeners.OnCountryPickerListener
import dagger.hilt.android.AndroidEntryPoint
import dev.whyoleg.ktd.api.TelegramObject
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class NewPhoneNumberFragment() : BottomSheetDialogFragment(), OnCountryPickerListener, GlobalBroker.Publisher, GlobalBroker.Subscriber {

    @TelegramServ
    @Inject
    lateinit var t_service : Service
    private lateinit var rootView : View
    private lateinit var binding: NewFragmentPhoneNumberBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        binding = NewFragmentPhoneNumberBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var result : TelegramObject? = null
        val countryNameEdit : EditText = binding.countryName
        countryNameEdit.setOnClickListener {
            var picker : CountryPicker
            var builder = this.context?.let { it1 ->
                CountryPicker.Builder().with(it1)
                    .listener(this)
            }
            picker = builder?.build()!!
            picker.showDialog(this.activity as AppCompatActivity)
        }
        val textInputLayout: TextInputLayout = binding.countryEdit
        textInputLayout.setEndIconOnClickListener {
            // do something.
            var picker : CountryPicker
            var builder = this.context?.let { it1 ->
                CountryPicker.Builder().with(it1)
                    .listener(this)
            }
            picker = builder?.build()!!
            picker.showDialog(this.activity as AppCompatActivity)
        }

        binding.numberConfirmBtn.setOnClickListener {
            lifecycleScope.launch {
                try {
                    showLoading(false)
                    withContext(Dispatchers.IO) {
                        var task = async{
                                (t_service as TelegramService).callNumberConfirm(binding.countryDialCode.text.toString() +
                                    binding.numberEdit.text.toString())
                        }
                        result = task.await()
                    }
                    if(result != null){
                        dismiss()
                        publish(PhoneChangedEvent(binding.countryDialCode.text.toString() +
                                binding.numberEdit.text.toString()))
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

    override fun onSelectCountry(country: Country?) {
        binding.countryName.setText(country?.name)
        binding.countryDialCode.setText(country?.dialCode)
    }

    fun showLoading(show : Boolean){
        binding.numberConfirmBtn.isEnabled = show
        binding.numberEdit.isEnabled = show
    }

}
