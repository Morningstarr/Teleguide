package com.mongodb.alliance.ui.authorization

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.authorization.SignInListener
import com.mongodb.alliance.authorization.SignListener
import com.mongodb.alliance.databinding.FragmentSignInBinding
import com.mongodb.alliance.events.SuccessSignIn
import com.mongodb.alliance.ui.FolderActivity
import io.realm.mongodb.App
import io.realm.mongodb.User
import kotlinx.coroutines.InternalCoroutinesApi
import org.greenrobot.eventbus.EventBus
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
        binding = FragmentSignInBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailEdit = binding.enterEmailSin
        val passEdit = binding.enterPassSin

        binding.btnEnter.setOnClickListener{
            loading(false)
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
                    /*else{
                        this.activity?.let { it1 -> onLoginFailed("Некорректное имя пользователя или пароль", it1) }
                        loading(true)
                    }*/
                } else {
                    this.activity?.let { it1 -> onLoginFailed("Заполните все поля!", it1) }
                    loading(true)
                }
            }
            catch(e:Exception){
                Timber.e(e.message)
                Toast.makeText(activity, e.message, Toast.LENGTH_LONG).show()
                loading(true)
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
        try{
            if(result != null) {
                if (result.isSuccess) {
                    dismiss()
                    val intent = Intent(activity, FolderActivity::class.java)
                    //intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    activity?.finish()
                    loading(true)
                } else {
                    throw Exception("An error occured, while performing a request: " + result.error.errorMessage)
                }
            }
            else{
                throw Exception("The request cannot be completed. Please, try again later")
            }
        }
        catch(e:Exception){
            if(!this.isVisible) {
                if (e.message.toString().contains("binding")) {
                    EventBus.getDefault().post(SuccessSignIn("ok"))
                } else {
                    EventBus.getDefault().post(SuccessSignIn(e.message.toString()))
                }
            }
            else{
                Toast.makeText(activity, e.message.toString(), Toast.LENGTH_LONG).show()
                loading(true)
            }
        }
    }

    fun loading(show : Boolean){
        binding.btnEnter.isEnabled = show
        binding.googleBtnSin.isEnabled = show
        binding.facebookBtnSin.isEnabled = show
        binding.enterEmailSin.isEnabled = show
        binding.enterPassSin.isEnabled = show
        if(show) {
            binding.shadow.visibility = View.VISIBLE
            binding.shadowFacebookSin.visibility = View.VISIBLE
            binding.shadowGoogleSin.visibility = View.VISIBLE
        }
        else{
            binding.shadow.visibility = View.INVISIBLE
            binding.shadowFacebookSin.visibility = View.INVISIBLE
            binding.shadowGoogleSin.visibility = View.INVISIBLE
        }

    }
}