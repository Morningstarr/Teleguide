package com.mongodb.alliance.model

import android.app.AlertDialog
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


internal class ChannelFindAdapter(data: OrderedRealmCollection<ChannelRealm>, var folderName : String) : GlobalBroker.Publisher, RealmRecyclerViewAdapter<ChannelRealm, ChannelFindAdapter.ChannelViewHolder?>(data, true) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.channel_find_view, parent, false)
        return ChannelViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val obj: ChannelRealm? = getItem(position)
        holder.data = obj
        holder.name.text = obj?.name
        holder.type.text = obj?.typeEnum?.displayName

        holder.itemView.setOnClickListener {
            val builder = AlertDialog.Builder(ChannelProj.getContxt())
            builder.setTitle("Attention")
            builder.setMessage("Do you really want to add following channel into current folder?")

            builder.setPositiveButton(android.R.string.yes) { dialog, which ->
                addToFolder(holder.name.text as String, folderName)
            }

            builder.show()
        }
    }

    private fun addToFolder(channelName : String, folderName: String){
        val bgRealm = Realm.getDefaultInstance()
        bgRealm!!.executeTransaction {
            val item = it.where<ChannelRealm>().equalTo("name", channelName).findFirst()
            //item?.folder = findFolderId(folderName)
        }

        bgRealm.close()
    }

    private fun findFolderId(name : String) : ObjectId {
        val bgRealm = Realm.getDefaultInstance()
        lateinit var folderId : ObjectId
        bgRealm!!.executeTransaction {
            val item = it.where<FolderRealm>().equalTo("name", name).findFirst()
            folderId = item?._id!!
        }

        bgRealm.close()
        return folderId
    }


    internal inner class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.channel_find_name)
        var type: TextView = view.findViewById(R.id.channel_find_type)
        var data: ChannelRealm? = null

    }
}