package com.mongodb.alliance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope

import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.databinding.FragmentNewPasswordBinding
import com.mongodb.alliance.model.UserRealm
import io.realm.Realm
import io.realm.com_mongodb_alliance_model_UserRealmRealmProxy
import io.realm.kotlin.where
import io.realm.mongodb.App
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import timber.log.Timber


class NewPasswordFragment (var token : String = "", var tokenId : String = ""): BottomSheetDialogFragment() {

    private lateinit var binding : FragmentNewPasswordBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        binding = FragmentNewPasswordBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.newPasswordChangeBtn.setOnClickListener{
            val passwordEdit = binding.fragmentNewPasswordEdit
            val oldPasswordEdit = binding.fragmentOldPasswordEdit
            val repeatPasswordEdit = binding.fragmentNewPasswordConfirmEdit
            passwordEdit.isEnabled = false
            oldPasswordEdit.isEnabled = false
            repeatPasswordEdit.isEnabled = false
            binding.newPasswordChangeBtn.isEnabled = false
            binding.shadowChange.visibility = View.INVISIBLE
            try {
                if (passwordEdit.text.toString() == repeatPasswordEdit.text.toString()) {
                    if (passwordEdit.text.toString().length > 6) {
                        val realm = Realm.getDefaultInstance()
                        val appUserRealm = realm.where<UserRealm>().equalTo("user_id", channelApp.currentUser()?.id).findFirst() as UserRealm
                        val userEmail = (appUserRealm as com_mongodb_alliance_model_UserRealmRealmProxy).`realmGet$name`()
                        channelApp.emailPassword.resetPasswordAsync(token, tokenId, binding.fragmentNewPasswordEdit.text.toString()){
                           if (it.isSuccess) {
                               Toast.makeText(activity, "Password successfully changed", Toast.LENGTH_LONG).show()
                               dismiss()
                           } else {
                               Toast.makeText(activity, "Failed to change password", Toast.LENGTH_LONG).show()
                           }
                        }
                    } else {
                        Toast.makeText(
                            activity,
                            "Длина пароля должна превышать 6 символов!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(activity, "Пароли не совпадают!", Toast.LENGTH_SHORT).show()
                }
            }
            catch(e:Exception){
                Timber.e(e.message)
                Toast.makeText(activity, e.message, Toast.LENGTH_SHORT).show()
            }
            finally{
                passwordEdit.isEnabled = true
                oldPasswordEdit.isEnabled = true
                repeatPasswordEdit.isEnabled = true
                binding.newPasswordChangeBtn.isEnabled = true
                binding.shadowChange.visibility = View.VISIBLE
            }
            //dismiss()
        }
    }

}
