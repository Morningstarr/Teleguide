package com.mongodb.alliance.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.daimajia.swipe.SwipeLayout
import com.mongodb.alliance.R
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.events.ChannelSaveEvent
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.events.SelectChatFromArrayEvent
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.InternalCoroutinesApi
import org.bson.types.ObjectId
import org.greenrobot.eventbus.EventBus
import java.util.*
import kotlin.collections.ArrayList
import kotlin.time.ExperimentalTime


internal class ChannelArrayAdapter(var data: MutableList<ChannelRealm>, var folderName : String) : GlobalBroker.Publisher,
    RecyclerView.Adapter<ChannelArrayAdapter.ChannelArrayViewHolder?>(), Filterable {

    private var selectedChats : MutableList<ChannelRealm> = ArrayList()
    private  var channelsArrFilterList : MutableList<ChannelRealm> = ArrayList()

    init {
        channelsArrFilterList = data
    }

    override fun getItemCount(): Int {
        return channelsArrFilterList.size
    }

    fun getItem(position: Int) : ChannelRealm{
        return channelsArrFilterList[position]
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelArrayViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.new_channel_array_view, parent, false)
        return ChannelArrayViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ChannelArrayViewHolder, position: Int) {
        val obj: ChannelRealm? = getItem(position)
        holder.data = obj
        holder.name.text = obj?.displayName
        holder.placeholder.text = obj?.displayName?.elementAt(0).toString()

        holder.itemLayout.setOnClickListener {
            if(!selectedChats.contains(holder.data)) {
                EventBus.getDefault().post(SelectChatFromArrayEvent(true))
                holder.isSelected = true
                holder.checkBtn.visibility = View.VISIBLE
                holder.data?.let { it1 -> selectedChats.add(it1) }
            }
            else{
                EventBus.getDefault().post(SelectChatFromArrayEvent(false))
                holder.isSelected = false
                holder.checkBtn.visibility = View.GONE
                holder.data?.let { it1 -> selectedChats.remove(it1) }
            }
        }

        holder.checkLayout.setOnLongClickListener {
            if(selectedChats.size <= 0){
                EventBus.getDefault().post(SelectChatFromArrayEvent(true))
                holder.checkBtn.visibility = View.VISIBLE
                holder.data?.let { it1 -> selectedChats.add(it1) }
                holder.isSelected = true
                return@setOnLongClickListener true
            }
            else{
                return@setOnLongClickListener false
            }
        }

        holder.checkLayout.setOnClickListener {
            if(selectedChats.size > 0){
                if(!holder.isSelected) {
                    EventBus.getDefault().post(SelectChatFromArrayEvent(true))
                    holder.checkBtn.visibility = View.VISIBLE
                    holder.data?.let { it1 -> selectedChats.add(it1) }
                    holder.isSelected = true
                }
                else{
                    EventBus.getDefault().post(SelectChatFromArrayEvent(false))
                    holder.isSelected = false
                    holder.checkBtn.visibility = View.GONE
                    holder.data?.let { it1 -> selectedChats.remove(it1) }
                }
            }
        }

        if(selectedChats.find { it._id == holder.data?._id } == null){
            holder.checkBtn.visibility = View.GONE
        }
        else{
            holder.checkBtn.visibility = View.VISIBLE
        }
    }

    fun addToFolder(folderId: ObjectId){
        val bgRealm = Realm.getDefaultInstance()

        if(bgRealm != null) {
            for(item in selectedChats) {
                item.folder = findFolder(folderId)

                bgRealm.executeTransactionAsync { realm ->
                    realm.insert(item)
                }
            }

            bgRealm.close()
        }
        else{
            EventBus.getDefault().post(NullObjectAccessEvent("The realm is null!"))
        }
    }

    private fun findFolder(folderId : ObjectId) : FolderRealm? {
        val bgRealm = Realm.getDefaultInstance()
        var folder : FolderRealm? = null
        bgRealm.executeTransaction {
            folder = it.where<FolderRealm>().equalTo("_id", folderId).findFirst()
            folder?.nestedCount = folder?.nestedCount?.plus(1)!!
        }

        bgRealm.close()
        return folder
    }

    fun cancelSelection(){
        selectedChats.clear()
        notifyDataSetChanged()
    }

    fun setDataList(chats: MutableList<ChannelRealm>) {
        channelsArrFilterList = chats
        notifyDataSetChanged()
    }


    internal inner class ChannelArrayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.channel_arr_channel_name)
        var placeholder: TextView = view.findViewById(R.id.channel_arr_placeholder)
        var itemLayout: LinearLayout = view.findViewById(R.id.channels_arr_item_layout)
        var checkLayout: ConstraintLayout = view.findViewById(R.id.channel_arr_check_layout)
        var checkBtn: ImageView = view.findViewById(R.id.channel_arr_check_channel)
        var data: ChannelRealm? = null
        var isSelected: Boolean = false

    }

    fun filterResults(text : String) {
        if (text.isEmpty()) {
            channelsArrFilterList = data
        } else {
            val resultList : MutableList<ChannelRealm> = ArrayList()
            for (row in data) {
                if (row.displayName.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                    resultList.add(row)
                }
            }
            channelsArrFilterList = resultList
        }
        setDataList(channelsArrFilterList)
    }

    override fun getFilter() : Filter {
        return NamesFilter(this)
    }

    private class NamesFilter(private val adapter: ChannelArrayAdapter) : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            return FilterResults()
        }

        @ExperimentalTime
        @InternalCoroutinesApi
        override fun publishResults(
            constraint: CharSequence,
            results: FilterResults?
        ) {
            adapter.filterResults(constraint.toString())
        }
    }
}