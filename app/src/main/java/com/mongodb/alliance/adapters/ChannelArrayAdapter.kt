package com.mongodb.alliance.adapters

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import cafe.adriel.broker.subscribe
import com.mongodb.alliance.ChannelProj
import com.mongodb.alliance.R
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.ChannelSaveEvent
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import io.realm.kotlin.where
import org.bson.types.ObjectId
import androidx.lifecycle.lifecycleScope


internal class ChannelArrayAdapter(var data: ArrayList<ChannelRealm>, var folderName : String) : GlobalBroker.Publisher,
    RecyclerView.Adapter<ChannelArrayAdapter.ChannelViewHolder?>() {

    override fun getItemCount(): Int {
        return data.size
    }

    fun getItem(position: Int) : ChannelRealm{
        return data[position]
    }


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
            /*val builder = AlertDialog.Builder(ChannelProj.getContxt())
            builder.setTitle("Attention")
            builder.setMessage("Do you really want to add following channel into current folder?")
            builder.setPositiveButton(android.R.string.yes) { dialog, which ->
                publish(ChannelSaveEvent(1))
            }

            builder.show()*/
            addToFolder(holder.data?.name!!, folderName)
            publish(ChannelSaveEvent(1))
        }
    }

    private fun addToFolder(channelName : String, folderName: String){
        val bgRealm = Realm.getDefaultInstance()

        val item = data.find { it.name == channelName }
        item?.folder = findFolder(folderName)

        bgRealm!!.executeTransactionAsync { realm ->
            realm.insert(item as ChannelRealm)
        }

        bgRealm.close()
    }

    private fun findFolder(name : String) : FolderRealm? {
        val bgRealm = Realm.getDefaultInstance()
        var folder : FolderRealm? = null
        bgRealm!!.executeTransaction {
            folder = it.where<FolderRealm>().equalTo("name", name).findFirst()
        }

        bgRealm.close()
        return folder
    }


    internal inner class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.channel_find_name)
        var type: TextView = view.findViewById(R.id.channel_find_type)
        var data: ChannelRealm? = null

    }
}