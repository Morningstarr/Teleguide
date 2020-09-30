package com.mongodb.alliance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView

import com.google.android.material.bottomsheet.BottomSheetDialogFragment



class NewEmailFragment (var hint : String): BottomSheetDialogFragment() {


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        var view = inflater.inflate(R.layout.fragment_new_email, container, false)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<EditText>(R.id.new_email_edit).hint = hint
        view.findViewById<TextView>(R.id.new_email_change_btn).setOnClickListener{
            //todo changeEmail
            dismiss()
        }
    }
}
