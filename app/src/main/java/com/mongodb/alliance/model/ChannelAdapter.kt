package com.mongodb.alliance.model

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.*
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.mongodb.alliance.ChannelProj
import com.mongodb.alliance.R
import com.mongodb.alliance.TAG
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmRecyclerViewAdapter
import io.realm.kotlin.where
import org.bson.types.ObjectId


internal class ChannelAdapter(data: OrderedRealmCollection<ChannelRealm>) : GlobalBroker.Publisher, RealmRecyclerViewAdapter<ChannelRealm, ChannelAdapter.ChannelViewHolder?>(data, true) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.channel_view, parent, false)
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
        //startActivity(ChannelProj.getContxt().applicationContext, intent, null)
        //startActivity(baseContext, intent)
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