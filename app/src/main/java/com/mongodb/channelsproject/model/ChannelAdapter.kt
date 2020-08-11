package com.mongodb.channelsproject.model

import android.util.Log
import android.view.*
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mongodb.channelsproject.R
import com.mongodb.channelsproject.TAG
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmRecyclerViewAdapter
import io.realm.kotlin.where
import org.bson.types.ObjectId


internal class ChannelAdapter(data: OrderedRealmCollection<ChannelRealm>) : RealmRecyclerViewAdapter<ChannelRealm, ChannelAdapter.ChannelViewHolder?>(data, true) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.channel_view, parent, false)
        return ChannelViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val obj: ChannelRealm? = getItem(position)
        holder.data = obj
        holder.name.text = obj?.name
        holder.type.text = obj?.typeEnum?.displayName

        holder.itemView.setOnClickListener {
            run {
                val popup = PopupMenu(holder.itemView.context, holder.menu)
                val menu = popup.menu

                if (holder.data?.typeEnum != ChannelType.chat)
                {
                    menu.add(0, ChannelType.chat.ordinal, Menu.NONE, ChannelType.chat.displayName)
                }
                if (holder.data?.typeEnum != ChannelType.groupChat)
                {
                    menu.add(0, ChannelType.groupChat.ordinal, Menu.NONE, ChannelType.groupChat.displayName)
                }
                if (holder.data?.typeEnum != ChannelType.channel)
                {
                    menu.add(0, ChannelType.channel.ordinal, Menu.NONE, ChannelType.channel.displayName)
                }

                val deleteCode = -1
                menu.add(0, deleteCode, Menu.NONE, "Delete Channel")

                popup.setOnMenuItemClickListener { item: MenuItem? ->
                    var type: ChannelType? = null
                    when (item!!.itemId) {
                        ChannelType.chat.ordinal -> {
                            type = ChannelType.chat
                        }
                        ChannelType.groupChat.ordinal -> {
                            type = ChannelType.groupChat
                        }
                        ChannelType.channel.ordinal -> {
                            type = ChannelType.channel
                        }
                        deleteCode -> {
                            removeAt(holder.data?._id!!)
                        }
                    }

                    if (type != null) {
                        Log.v(TAG(), "Changing type of ${holder.data?.name} (${holder.data?._id}) to $type")
                        changeType(type, holder.data?._id)
                    }
                    true
                }
                popup.show()
            }}
    }

    private fun changeType(type: ChannelType, _id: ObjectId?) {
        val bgRealm = Realm.getDefaultInstance()

        bgRealm!!.executeTransaction {
            val item = it.where<ChannelRealm>().equalTo("_id", _id).findFirst()
            item?.typeEnum = type
        }

        bgRealm.close()
    }

    private fun removeAt(id: ObjectId) {
        val bgRealm = Realm.getDefaultInstance()

        bgRealm!!.executeTransaction {

            val item = it.where<ChannelRealm>().equalTo("_id", id).findFirst()
            item?.deleteFromRealm()
        }

        bgRealm.close()
    }

    internal inner class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.name)
        var type: TextView = view.findViewById(R.id.type)
        var data: ChannelRealm? = null
        var menu: TextView = view.findViewById(R.id.menu)

    }
}