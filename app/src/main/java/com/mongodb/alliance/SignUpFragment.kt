package com.mongodb.alliance


import android.app.Dialog
import android.content.DialogInterface.OnShowListener
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.databinding.FragmentSignUpBinding
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.time.ExperimentalTime

@InternalCoroutinesApi
@ExperimentalTime
class SignUpFragment: BottomSheetDialogFragment(), SignListener {

    private lateinit var binding: FragmentSignUpBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
        //var view = inflater.inflate(R.layout.fragment_sign_up, container, false)
        //return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailEdit = binding.enterEmailSup
        val passEdit = binding.enterPassSup
        val repPassEdit = binding.repeatPassSup

        binding.btnCreate.setOnClickListener{
            if(emailEdit.text.toString() != "" && passEdit.text.toString() != ""
                && repPassEdit.text.toString() != ""){
                if(passEdit.text.toString() == repPassEdit.text.toString()){
                    if(this.activity?.let { it1 ->
                            validateCredentials(emailEdit.text.toString(), passEdit.text.toString(),
                                it1
                            )
                        }!!){
                        //this.activity?.let { it1 -> onLoginAfterSignUpSuccess(it1) }
                        this.activity?.let { it1 ->
                            login(true, true, emailEdit.text.toString(),
                                passEdit.text.toString(), it1
                            )
                        }
                        dismiss()
                        var bsf = SignInFragment()
                        fragmentManager?.let { it1 ->
                            bsf.show(
                                it1,
                                bsf.tag
                            )
                        }
                    }
                }
                else{
                    this.activity?.let { it1 -> onLoginFailed( "Пароли не совпадают!", it1) }
                }
            }
            else{
                this.activity?.let { it1 -> onLoginFailed("Заполните все поля!", it1) }
            }
        }

        binding.labelIsAcc.setOnClickListener{
            dismiss()
            var bsf = SignInFragment()
            fragmentManager?.let { it1 ->
                bsf.show(
                    it1,
                    bsf.tag
                )
            }
        }

    }

}