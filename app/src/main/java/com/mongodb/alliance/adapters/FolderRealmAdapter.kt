package com.mongodb.alliance.adapters

import android.view.*
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.mongodb.alliance.R
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.model.FolderRealm
import com.mongodb.alliance.events.OpenFolderEvent
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmRecyclerViewAdapter
import io.realm.kotlin.where
import org.bson.types.ObjectId
import org.greenrobot.eventbus.EventBus


internal class FolderRealmAdapter(data: OrderedRealmCollection<FolderRealm>) : GlobalBroker.Publisher, RealmRecyclerViewAdapter<FolderRealm, FolderRealmAdapter.FolderViewHolder?>(data, true) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.folder_realm_view, parent, false)
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
                val openCode = 1
                menu.add(0, openCode, Menu.NONE, "Open Folder")
                menu.add(0, deleteCode, Menu.NONE, "Delete Folder")

                popup.setOnMenuItemClickListener { item: MenuItem? ->
                    if (item != null) {
                        when (item.itemId) {
                            deleteCode -> {
                                holder.data?._id?.let { it1 -> removeAt(it1) }
                            }
                            openCode -> {
                                publish(
                                    OpenFolderEvent(
                                        holder.data?._id.toString()
                                    )
                                )
                            }
                        }
                    }
                    else{
                        EventBus.getDefault().post(NullObjectAccessEvent("The item is null!"))
                    }
                    true
                }
                popup.show()
            }}
    }

    private fun removeAt(id: ObjectId) {
        val bgRealm = Realm.getDefaultInstance()

        if(bgRealm != null) {
            bgRealm.executeTransaction {

                val item = it.where<FolderRealm>().equalTo("_id", id).findFirst()
                item?.deleteFromRealm()
            }

            bgRealm.close()
        }
        else{
            EventBus.getDefault().post(NullObjectAccessEvent("The realm object is null!"))
        }
    }

    internal inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.folder_realm_view_name)
        var data: FolderRealm? = null
        var menu: TextView = view.findViewById(R.id.folder_realm_view_menu)

    }
}