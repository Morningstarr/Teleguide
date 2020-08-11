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


internal class FolderAdapter(data: OrderedRealmCollection<FolderRealm>) : RealmRecyclerViewAdapter<FolderRealm, FolderAdapter.FolderViewHolder?>(data, true) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.channel_view, parent, false)
        return FolderViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val obj: FolderRealm? = getItem(position)
        holder.data = obj
        holder.name.text = obj?.name

        holder.itemView.setOnClickListener {
            run {
                val popup = PopupMenu(holder.itemView.context, holder.menu)
                val menu = popup.menu

                val deleteCode = -1
                menu.add(0, deleteCode, Menu.NONE, "Delete Folder")

                popup.setOnMenuItemClickListener { item: MenuItem? ->
                    when (item!!.itemId) {
                        deleteCode -> {
                            removeAt(holder.data?._id!!)
                        }
                    }
                    true
                }
                popup.show()
            }}
    }

    private fun removeAt(id: ObjectId) {
        val bgRealm = Realm.getDefaultInstance()

        bgRealm!!.executeTransaction {

            val item = it.where<FolderRealm>().equalTo("_id", id).findFirst()
            item?.deleteFromRealm()
        }

        bgRealm.close()
    }

    internal inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.name)
        var data: FolderRealm? = null
        var menu: TextView = view.findViewById(R.id.menu)

    }
}