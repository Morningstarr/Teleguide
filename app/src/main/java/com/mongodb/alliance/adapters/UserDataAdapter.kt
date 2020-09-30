package com.mongodb.alliance.adapters

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.mongodb.alliance.R
import com.mongodb.alliance.UserDataType
import com.mongodb.alliance.events.ChangeUserDataEvent
import com.mongodb.alliance.events.ChannelSaveEvent
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.FolderRealm
import com.mongodb.alliance.model.UserData
import io.realm.Realm
import io.realm.kotlin.where
import org.greenrobot.eventbus.EventBus

internal class UserDataAdapter(var data: ArrayList<UserData>) : GlobalBroker.Publisher,
    RecyclerView.Adapter<UserDataAdapter.UserDataViewHolder?>() {

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

        holder.itemView.setOnClickListener {
            when(holder.data?.dataType){
                UserDataType.email -> {
                    obj?.dataValue?.let { it1 -> ChangeUserDataEvent(0, it1) }?.let { it2 ->
                        publish(
                            it2
                        )
                    }
                }
                UserDataType.password -> {
                    publish(ChangeUserDataEvent(3))
                }
                UserDataType.phoneNumber -> {
                    obj?.dataValue?.let { it1 -> ChangeUserDataEvent(1, it1) }?.let { it2 ->
                        publish(
                            it2
                        )
                    }
                }
                UserDataType.telegramAccount -> {
                    obj?.dataValue?.let { it1 -> ChangeUserDataEvent(2, it1) }?.let { it2 ->
                        publish(
                            it2
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