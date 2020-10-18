package com.mongodb.alliance.adapters

import android.telephony.PhoneNumberFormattingTextWatcher
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.marginTop
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.mongodb.alliance.R
import com.mongodb.alliance.events.*
import com.mongodb.alliance.model.UserData
import com.mongodb.alliance.model.UserDataType
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


internal class UserDataAdapter(var data: ArrayList<UserData>) : GlobalBroker.Publisher,
    RecyclerView.Adapter<UserDataAdapter.UserDataViewHolder?>() {

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ChangePhoneHintEvent) {
        data[1].dataHint = event.hint
        //data[1].
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ChangeTelegramHintEvent) {
        data[2].dataHint = event.hint
    }

    override fun getItemCount(): Int {
        return data.size
    }

    fun getItem(position: Int) : UserData {
        return data[position]
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserDataViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.user_data_view, parent, false)
        return UserDataViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: UserDataViewHolder, position: Int) {
        val obj: UserData? = getItem(position)
        holder.data = obj
        holder.record.text = obj?.dataValue
        holder.hint.text = obj?.dataHint

        if(obj?.dataType == UserDataType.password){
            holder.record.transformationMethod = PasswordTransformationMethod.getInstance()
        }

        if(obj?.dataType == UserDataType.telegramAccount && obj.dataValue == ""){
            holder.record.visibility = View.GONE
            val params =
                holder.hint.layoutParams as LinearLayout.LayoutParams
            params.setMargins(0, 95, 0, 0) //substitute parameters for left, top, right, bottom
            holder.hint.layoutParams = params
        }

        if(obj?.dataType == UserDataType.phoneNumber){
            /*holder.record.addTextChangedListener(object : PhoneNumberFormattingTextWatcher("+380") {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun afterTextChanged(editable: Editable) {
                    val text = holder.record.text.toString()
                    val textLength = holder.record.text.length

                    if (text.endsWith("-") || text.endsWith(" ")) {
                        return
                    }

                    if (textLength == 1) {
                        if (!text.contains("(")) {
                            setText(StringBuilder(text).insert(text.length - 1, "(").toString())
                        }
                    } else if (textLength == 5) {
                        if (!text.contains(")")) {
                            setText(StringBuilder(text).insert(text.length - 1, ")").toString())
                        }
                    } else if (textLength == 6) {
                        setText(StringBuilder(text).insert(text.length - 1, " ").toString())
                    } else if (textLength == 10) {
                        if (!text.contains("-")) {
                            setText(StringBuilder(text).insert(text.length - 1, "-").toString())
                        }
                    } else if (textLength == 15) {
                        if (text.contains("-")) {
                            setText(StringBuilder(text).insert(text.length - 1, " ext").toString())
                        }
                    }
                }

                private fun setText(text: String){
                    holder.record.removeTextChangedListener(this)
                    holder.record.editableText.replace(0, holder.record.text.length, text)
                    holder.record.addTextChangedListener(this)
                }
            })*/
            if(obj.dataValue == "") {
                holder.record.visibility = View.GONE
                val params =
                    holder.hint.layoutParams as LinearLayout.LayoutParams
                params.setMargins(0, 75, 0, 0) //substitute parameters for left, top, right, bottom
                holder.hint.layoutParams = params
            }
        }

        holder.itemView.setOnClickListener {
            when(holder.data?.dataType){
                UserDataType.password -> {
                    publish(ChangeUserDataEvent(3))
                }
                UserDataType.phoneNumber -> {
                    if(holder.data?.disabled != null && !(holder.data?.disabled as Boolean)) {
                        if (obj != null) {
                            publish(
                                ChangeUserDataEvent(1, obj.dataValue)
                            )
                        }
                    }
                }
                UserDataType.telegramAccount -> {
                    if (obj != null) {
                        publish(
                            ChangeUserDataEvent(2, obj.dataValue)
                        )
                    }
                }
            }
        }
    }


    internal inner class UserDataViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var record: TextView = view.findViewById(R.id.user_data_record)
        var hint: TextView = view.findViewById(R.id.user_data_hint)
        var data: UserData? = null

    }
}