package com.mongodb.alliance.adapters

import android.content.Intent
import android.net.Uri
import android.view.*
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.mongodb.alliance.R
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.ChannelType
import com.mongodb.alliance.events.OpenChannelEvent
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmRecyclerViewAdapter
import io.realm.kotlin.where
import org.bson.types.ObjectId


internal class ChannelRealmAdapter(data: OrderedRealmCollection<ChannelRealm>) : GlobalBroker.Publisher, RealmRecyclerViewAdapter<ChannelRealm, ChannelRealmAdapter.ChannelViewHolder?>(data, true) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.channels_realm_view, parent, false)
        return ChannelViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val obj: ChannelRealm? = getItem(position)
        holder.data = obj
        holder.name.text = obj?.name
        holder.type.text = obj?.typeEnum?.displayName

        val openCode : Int = 22
        holder.itemView.setOnClickListener {
            run {
                val popup = PopupMenu(holder.itemView.context, holder.menu)
                val menu = popup.menu

                val deleteCode = -1
                menu.add(0, openCode, Menu.NONE, "Open Channel")
                menu.add(0, deleteCode, Menu.NONE, "Delete Channel")

                popup.setOnMenuItemClickListener { item: MenuItem? ->
                    var type: ChannelType? = null
                    when (item!!.itemId) {
                        deleteCode -> {
                            removeAt(holder.data?._id!!)
                        }
                        openCode ->{
                            openChannel(holder.data?.name!!)
                        }
                    }

                    true
                }
                popup.show()
            }}
    }

    private fun openChannel(username: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("http://www.t.me/$username")
        publish(OpenChannelEvent(intent))
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
        var name: TextView = view.findViewById(R.id.realm_view_channel_name)
        var type: TextView = view.findViewById(R.id.realm_view_channel_type)
        var data: ChannelRealm? = null
        var menu: TextView = view.findViewById(R.id.realm_view_channel_menu)

    }
}