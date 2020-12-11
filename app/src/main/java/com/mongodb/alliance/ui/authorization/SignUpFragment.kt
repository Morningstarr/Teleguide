package com.mongodb.alliance.ui.authorization

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.authorization.SignListener
import com.mongodb.alliance.authorization.SignUpListener
import com.mongodb.alliance.databinding.FragmentSignUpBinding
import com.mongodb.alliance.events.SuccessSignIn
import com.mongodb.alliance.ui.FolderActivity
import io.realm.mongodb.App
import kotlinx.coroutines.InternalCoroutinesApi
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime
class SignUpFragment: BottomSheetDialogFragment(),
    SignListener,
    SignUpListener{

    private lateinit var binding: FragmentSignUpBinding

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event : SuccessSignIn){
        if(event.message == "ok") {
            activity?.finish()
            val intent = Intent(activity?.baseContext, FolderActivity::class.java)
            startActivity(intent)
        }
        else{
            Toast.makeText(activity, event.message, Toast.LENGTH_LONG).show()
            loading(true)
            dismiss()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        EventBus.getDefault().register(this)
        binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailEdit = binding.enterEmailSup
        val passEdit = binding.enterPassSup
        val repPassEdit = binding.repeatPassSup

        binding.btnCreate.setOnClickListener{
            loading(false)

            try {
                if (emailEdit.text.toString() != "" && passEdit.text.toString() != ""
                    && repPassEdit.text.toString() != ""
                ) {
                    if (passEdit.text.toString() == repPassEdit.text.toString()) {
                        if (this.activity?.let { it1 ->
                                validateCredentials(
                                    emailEdit.text.toString(), passEdit.text.toString(),
                                    it1
                                )
                            }!!) {
                                    signUp(emailEdit.text.toString(), passEdit.text.toString())
                                }
                    } else {
                        this.activity?.let { it1 -> onLoginFailed("Пароли не совпадают!", it1) }
                        loading(true)
                    }
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

    fun loading(show : Boolean){
        binding.btnCreate.isEnabled = show
        binding.enterEmailSup.isEnabled = show
        binding.enterPassSup.isEnabled = show
        binding.repeatPassSup.isEnabled = show
        binding.googleBtnSup.isEnabled = show
        binding.facebookBtnSup.isEnabled = show
        if(show) {
            binding.shadow.visibility = View.VISIBLE
            binding.shadowFacebookSup.visibility = View.VISIBLE
            binding.shadowGoogleSup.visibility = View.VISIBLE
        }
        else{
            binding.shadow.visibility = View.INVISIBLE
            binding.shadowFacebookSup.visibility = View.INVISIBLE
            binding.shadowGoogleSup.visibility = View.INVISIBLE
        }

    }

    override fun onResult(result: App.Result<Void>?) {
        if(result != null) {
            if (result.isSuccess) {
                Toast.makeText(activity, "Successfully registered", Toast.LENGTH_SHORT).show()
                val fragmentTransaction: FragmentTransaction? = fragmentManager
                    ?.beginTransaction()

                val bsf = SignInFragment()
                fragmentTransaction?.add(0, bsf)

                bsf.signIn(false, binding.enterEmailSup.text.toString(), binding.enterPassSup.text.toString())

            }
            else{
                Toast.makeText(activity, result.error.errorMessage, Toast.LENGTH_LONG).show()
                loading(true)
            }
        }
    }



}