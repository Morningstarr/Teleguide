package com.mongodb.alliance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView

import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.databinding.FragmentSignInBinding
import com.mongodb.alliance.databinding.FragmentSignUpBinding
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.time.ExperimentalTime

@ExperimentalTime
@InternalCoroutinesApi
class SignInFragment: BottomSheetDialogFragment(), SignListener {

    private lateinit var binding : FragmentSignInBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        //var view = inflater.inflate(R.layout.fragment_sign_in, container, false)
        //return view
        binding = FragmentSignInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailEdit = binding.enterEmailSin
        val passEdit = binding.enterPassSin

        binding.btnEnter.setOnClickListener{
            if(emailEdit.text.toString() != "" && passEdit.text.toString() != ""){
                if(this.activity?.let { it1 ->
                        validateCredentials(emailEdit.text.toString(), passEdit.text.toString(),
                            it1
                        )
                    }!!){
                    //this.activity?.let { it1 -> onLoginAfterSignUpSuccess(it1) }
                    this.activity?.let { it1 ->
                        login(false, false, emailEdit.text.toString(),
                            passEdit.text.toString(), it1
                        )
                    }
                    //this.activity?.let { it1 -> onLoginSuccess(it1) }
                }
            }
            else{
                this.activity?.let { it1 -> onLoginFailed("Заполните все поля!", it1) }
            }
        }

        binding.labelCreateAcc.setOnClickListener{
            dismiss()
            var bsf = SignUpFragment()
            fragmentManager?.let { it1 ->
                bsf.show(
                    it1,
                    bsf.tag
                )
            }
        }
    }
}