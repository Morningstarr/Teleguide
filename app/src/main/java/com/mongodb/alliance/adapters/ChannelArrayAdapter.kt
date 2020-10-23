package com.mongodb.alliance.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.mongodb.alliance.R
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.events.ChannelSaveEvent
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import io.realm.kotlin.where
import org.greenrobot.eventbus.EventBus


internal class ChannelArrayAdapter(var data: ArrayList<ChannelRealm>, var folderName : String) : GlobalBroker.Publisher,
    RecyclerView.Adapter<ChannelArrayAdapter.ChannelViewHolder?>() {

    override fun getItemCount(): Int {
        return data.size
    }

    fun getItem(position: Int) : ChannelRealm{
        return data[position]
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.channels_array_view, parent, false)
        return ChannelViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val obj: ChannelRealm? = getItem(position)
        holder.data = obj
        holder.name.text = obj?.name
        holder.type.text = obj?.typeEnum?.displayName

        holder.itemView.setOnClickListener {
            holder.data?.name?.let { it1 -> addToFolder(it1, folderName) }
            publish(ChannelSaveEvent(1))
        }
    }

    private fun addToFolder(channelName : String, folderName: String){
        val bgRealm = Realm.getDefaultInstance()

        if(bgRealm != null) {
            val item = data.find { it.name == channelName }
            item?.folder = findFolder(folderName)

            bgRealm.executeTransactionAsync { realm ->
                realm.insert(item as ChannelRealm)
            }

            bgRealm.close()
        }
        else{
            EventBus.getDefault().post(NullObjectAccessEvent("The realm is null!"))
        }
    }

    private fun findFolder(name : String) : FolderRealm? {
        val bgRealm = Realm.getDefaultInstance()
        var folder : FolderRealm? = null
        bgRealm.executeTransaction {
            folder = it.where<FolderRealm>().equalTo("name", name).findFirst()
        }

        bgRealm.close()
        return folder
    }


    internal inner class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.array_view_channel_name)
        var type: TextView = view.findViewById(R.id.array_view_channel_type)
        var data: ChannelRealm? = null

    }
}