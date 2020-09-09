package com.mongodb.alliance

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText

open class CodeTextWatcher (private var left : View, private var right : View, private var flag : Boolean) : TextWatcher{
    override fun afterTextChanged(s: Editable?) {
        if (s != null) {
            if(s.isNotEmpty()) {
                if (flag) {
                    right.requestFocus()
                } else {
                    right.performClick()
                }
            }
            else{
                left.requestFocus()
            }
        }
        else{
            //todo eventbus error message handling
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

    }

}