package com.mongodb.alliance.ui.authorization

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.authorization.SignInListener
import com.mongodb.alliance.authorization.SignListener
import com.mongodb.alliance.databinding.FragmentSignInBinding
import io.realm.mongodb.App
import io.realm.mongodb.User
import kotlinx.coroutines.InternalCoroutinesApi
import timber.log.Timber
import kotlin.time.ExperimentalTime

@ExperimentalTime
@InternalCoroutinesApi
class SignInFragment: BottomSheetDialogFragment(),
    SignListener,
    SignInListener {

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
            binding.btnEnter.isEnabled = false
            binding.shadow.visibility = View.INVISIBLE
            binding.shadowFacebookSin.visibility = View.INVISIBLE
            binding.shadowGoogleSin.visibility = View.INVISIBLE
            binding.googleBtnSin.isEnabled = false
            binding.facebookBtnSin.isEnabled = false
            emailEdit.isEnabled = false
            passEdit.isEnabled = false
            try {
                if (emailEdit.text.toString() != "" && passEdit.text.toString() != "") {
                    if (this.activity?.let { it1 ->
                            validateCredentials(
                                emailEdit.text.toString(), passEdit.text.toString(),
                                it1
                            )
                        }!!)
                    {
                        signIn(false, emailEdit.text.toString(), passEdit.text.toString())
                    }
                    else{
                        this.activity?.let { it1 -> onLoginFailed("Некорректное имя пользователя или пароль", it1) }
                    }
                } else {
                    this.activity?.let { it1 -> onLoginFailed("Заполните все поля!", it1) }
                }
            }
            catch(e:Exception){
                Timber.e(e.message)
                Toast.makeText(activity, e.message, Toast.LENGTH_SHORT).show()
                binding.btnEnter.isEnabled = true
                binding.shadow.visibility = View.VISIBLE
                binding.shadowFacebookSin.visibility = View.VISIBLE
                binding.shadowGoogleSin.visibility = View.VISIBLE
                binding.googleBtnSin.isEnabled = true
                binding.facebookBtnSin.isEnabled = true
                emailEdit.isEnabled = true
                passEdit.isEnabled = true
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

    override fun onResult(result: App.Result<User>?) {
        dismiss()
        activity?.finish()
        binding.btnEnter.isEnabled = true
        binding.shadow.visibility = View.VISIBLE
        binding.shadowFacebookSin.visibility = View.VISIBLE
        binding.shadowGoogleSin.visibility = View.VISIBLE
        binding.googleBtnSin.isEnabled = true
        binding.facebookBtnSin.isEnabled = true
        binding.enterEmailSin.isEnabled = true
        binding.enterPassSin.isEnabled = true
    }
}