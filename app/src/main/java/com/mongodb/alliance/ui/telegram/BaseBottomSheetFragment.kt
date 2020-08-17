package com.mongodb.alliance.ui.telegram

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.R
import com.mongodb.alliance.databinding.FragmentPhoneNumberBinding
import dev.whyoleg.ktd.api.TdApi
import dev.whyoleg.ktd.api.TelegramObject
import kotlinx.coroutines.*
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime
open class BaseBottomSheetFragment : BottomSheetDialogFragment() {

    lateinit var input : EditText

    //private val binding get() = _binding!!

    fun onResult(result: TelegramObject) {
        Toast.makeText(context, result.toString(), Toast.LENGTH_SHORT)
    }

}