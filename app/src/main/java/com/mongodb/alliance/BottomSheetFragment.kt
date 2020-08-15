package com.mongodb.alliance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.ui.telegram.ConnectTelegramActivity
import dev.whyoleg.ktd.Telegram
import dev.whyoleg.ktd.TelegramClientConfiguration
import dev.whyoleg.ktd.api.TdApi
import dev.whyoleg.ktd.api.TelegramObject
import kotlinx.android.synthetic.main.fragment_bottom_sheet_dialog.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


@InternalCoroutinesApi
@ExperimentalTime
class BottomSheetFragment (_type : Int) : BottomSheetDialogFragment(), CoroutineScope {

    private var type : Int = _type
    lateinit var number : EditText

    val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    lateinit var mBehavior: BottomSheetBehavior<View>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        return when(type){
            1  -> {
                var view1 = inflater.inflate(R.layout.fragment_bottom_sheet_dialog, container, false)
                //var mBehavior = BottomSheetBehavior.from(bottom_sheet_layout1)
                return view1
            }
            2 -> inflater.inflate(R.layout.fragment_enter_code, container, false)
            3 -> inflater.inflate(R.layout.fragment_enter_password, container, false)
            else -> inflater.inflate(R.layout.fragment_bottom_sheet_dialog, container, false)

        }

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //mBehavior = BottomSheetBehavior.from(bottom_sheet_layout)
        number = view.findViewById<EditText>(R.id.user_input)
        lateinit var result : TelegramObject;
        view.findViewById<Button>(R.id.confirm_enter).setOnClickListener {
            launch {
                withContext(Dispatchers.IO) {
                    when(type){
                        1 -> { result = callNumberConfirm() }
                        2 -> { result = callCodeConfirm() }
                        3 -> { result = callPasswordConfirm() }
                        else -> { Toast.makeText((activity as ConnectTelegramActivity), "Unexpected error", Toast.LENGTH_SHORT) }
                    }

                    //mBehavior?.setState(STATE_HIDDEN)
                    //delay(1000)

                    onResult(result)
                }
            }
        }
    }


    private suspend fun callNumberConfirm(): TelegramObject {
        return (activity as ConnectTelegramActivity).client.exec(TdApi.SetAuthenticationPhoneNumber(number.text.toString(),null))
    }

    private suspend fun callCodeConfirm(): TelegramObject {
        return (activity as ConnectTelegramActivity).client.exec(TdApi.CheckAuthenticationCode(number.text.toString()))
    }

    private suspend fun callPasswordConfirm(): TelegramObject {
        return (activity as ConnectTelegramActivity).client.exec(TdApi.CheckAuthenticationPassword(number.text.toString()))
    }

    private fun onResult(result: TelegramObject) {
        Toast.makeText((activity as ConnectTelegramActivity), result.toString(), Toast.LENGTH_SHORT)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
    }

}