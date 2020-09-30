package com.mongodb.alliance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView

import com.google.android.material.bottomsheet.BottomSheetDialogFragment



class NewPasswordFragment (): BottomSheetDialogFragment() {


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        var view = inflater.inflate(R.layout.fragment_new_password, container, false)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.new_password_change_btn).setOnClickListener{
            //todo changePassword
            dismiss()
        }
    }
}
