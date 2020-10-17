package com.mongodb.alliance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.databinding.FragmentNewEmailBinding
import io.realm.Realm
import io.realm.mongodb.User
import io.realm.mongodb.functions.Functions
import io.realm.mongodb.mongo.MongoDatabase


class NewEmailFragment (var hint : String): BottomSheetDialogFragment() {

    private lateinit var binding : FragmentNewEmailBinding
    private lateinit var realm : Realm
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        binding = FragmentNewEmailBinding.inflate(layoutInflater)
        return binding.root
        //var view = inflater.inflate(R.layout.fragment_new_email, container, false)

        //return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val newEmailEdit = binding.newEmailEdit
        val changeBtn = binding.newEmailChangeBtn
        newEmailEdit.hint = hint

        changeBtn.setOnClickListener{
            //todo changeEmail
            /*realm = Realm.getDefaultInstance()
            val user = channelApp.currentUser()
            val mongoClient = user?.getMongoClient("mongodb-atlas")
            val functionsManager: Functions = channelApp.getFunctions(user)
            val args: List<String?> = listOf(user?.email)
            functionsManager.callFunctionAsync("updateUser", args, User::class.java) { result ->
                run {
                    if (result.isSuccess) {
                        Toast.makeText(activity, "success", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(activity, "failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }*/
            dismiss()
        }
    }
}
