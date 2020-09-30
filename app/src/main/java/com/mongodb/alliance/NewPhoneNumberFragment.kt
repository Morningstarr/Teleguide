package com.mongodb.alliance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputLayout
import com.mukesh.countrypicker.Country
import com.mukesh.countrypicker.CountryPicker
import com.mukesh.countrypicker.listeners.OnCountryPickerListener


class NewPhoneNumberFragment() : BottomSheetDialogFragment(), OnCountryPickerListener {

    private lateinit var rootView : View
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        var view = inflater.inflate(R.layout.new_fragment_phone_number, container, false)
        rootView = view
        return view
        //binding = FragmentCodeBinding.inflate(inflater, container, false)
        //return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val countryNameEdit : EditText = view.findViewById(R.id.country_name)
        countryNameEdit.setOnClickListener {
            var picker : CountryPicker
            var builder = this.context?.let { it1 ->
                CountryPicker.Builder().with(it1)
                    .listener(this)
            }
            picker = builder?.build()!!
            picker.showDialog(this.activity as AppCompatActivity)
        }
        val textInputLayout: TextInputLayout = view.findViewById(R.id.country_edit)
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
    }

    override fun onSelectCountry(country: Country?) {
        rootView.findViewById<EditText>(R.id.country_name).setText(country?.name)
        rootView.findViewById<EditText>(R.id.country_dial_code).setText(country?.dialCode)
    }

}
