package com.mongodb.alliance.adapters

import android.content.Intent
import android.net.Uri
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.mongodb.alliance.R
import com.mongodb.alliance.events.*
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.ChannelType
import com.mongodb.alliance.model.FolderRealm
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmRecyclerViewAdapter
import io.realm.kotlin.where
import kotlinx.coroutines.*
import org.bson.types.ObjectId
import org.greenrobot.eventbus.EventBus
import java.util.*
import kotlin.coroutines.CoroutineContext


class ChannelRealmAdapter(var data: MutableList<ChannelRealm>) :
    GlobalBroker.Publisher, /*RealmRecyclerViewAdapter<ChannelRealm, ChannelRealmAdapter.ChannelViewHolder?>(data, true),*/
        RecyclerSwipeAdapter<ChannelRealmAdapter.ChannelViewHolder>(),
        ItemTouchHelperAdapter, Filterable, CoroutineScope
{

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    var selectedChannels : MutableList<ChannelRealm> = ArrayList()
    var channelsFilterList : MutableList<ChannelRealm> = ArrayList()

    init {
        channelsFilterList = data
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.new_channels_realm_view, parent, false)
        return ChannelViewHolder(itemView)
    }

    private fun getItem(position: Int) : ChannelRealm? {
        return channelsFilterList/*data*/[position]
    }

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return R.id.chat_swipe_layout
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val obj: ChannelRealm? = getItem(position)
        holder.data = obj
        holder.name.text = obj?.name

        val openCode : Int = 22

        holder.swipeLayout.showMode = SwipeLayout.ShowMode.LayDown

        holder.swipeLayout.addDrag(SwipeLayout.DragEdge.Left, holder.bottomWrapper)

        holder.swipeLayout.isRightSwipeEnabled = false

        if (holder.data != null) {
            mItemManger.bindView(holder.itemView, position)
        }

        holder.swipeLayout.addSwipeListener(object : SwipeLayout.SwipeListener {
            override fun onClose(layout: SwipeLayout?) {
                //when the SurfaceView totally cover the BottomView.
            }

            override fun onUpdate(layout: SwipeLayout?, leftOffset: Int, topOffset: Int) {

            }

            override fun onStartOpen(layout: SwipeLayout?) {
                mItemManger.closeAllExcept(layout)
            }

            override fun onOpen(layout: SwipeLayout?) {
                //when the BottomView totally show.
            }

            override fun onStartClose(layout: SwipeLayout?) {

            }

            override fun onHandRelease(
                layout: SwipeLayout?,
                xvel: Float,
                yvel: Float
            ) {

            }
        })

        holder.itemView.setOnClickListener {
            /*run {
                val popup = PopupMenu(holder.itemView.context, holder.menu)
                val menu = popup.menu

                val deleteCode = -1
                menu.add(0, openCode, Menu.NONE, "Open Channel")
                menu.add(0, deleteCode, Menu.NONE, "Delete Channel")

                popup.setOnMenuItemClickListener { item: MenuItem? ->
                    var type: ChannelType? = null
                    if (item != null) {
                        when (item.itemId) {
                            deleteCode -> {
                                holder.data?._id?.let { it1 -> removeAt(it1) }
                            }
                            openCode ->{
                                holder.data?.name?.let { it1 -> openChannel(it1) }
                            }
                        }
                    }
                    else{
                        EventBus.getDefault().post(NullObjectAccessEvent("The item is null!"))
                    }

                    true
                }
                popup.show()
            }*/
        }

        holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_chat).setOnClickListener {
            mItemManger.closeAllItems()
            if (holder.data != null) {
                val isP = (holder.data as ChannelRealm).isPinned
                if (!isP) {
                    val temp = findPinned()
                    if (temp != null) {
                        EventBus.getDefault().post(ChannelPinDenyEvent("", holder))
                    } else {
                        holder.data?.let { it1 -> setPinned(it1, true) }
                        EventBus.getDefault().post(ChannelPinEvent("", holder.data!!))
                    }
                }
            }
        }
    }

    private fun findPinned() : ChannelRealm?{
        val bgRealm = Realm.getDefaultInstance()

        val result = bgRealm.where<ChannelRealm>().equalTo("isPinned", true)
            .findFirst()

        bgRealm.close()
        return result
    }

    private fun setPinned(channel : ChannelRealm, pinned : Boolean) {
        val bgRealm = Realm.getDefaultInstance()

        runBlocking {
            val tempChannel = bgRealm.where<ChannelRealm>().equalTo("_id", channel._id)
                .findFirst() as ChannelRealm
            bgRealm.executeTransaction { realm ->
                tempChannel.isPinned = pinned
            }
        }

        bgRealm.close()
    }

    fun setDataList(newData : MutableList<ChannelRealm>){
        //data = newData
        channelsFilterList = newData
        notifyDataSetChanged()
    }

    private fun openChannel(username: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("http://www.t.me/$username")
        publish(OpenChannelEvent(intent))
    }

    private fun removeAt(id: ObjectId) {
        val bgRealm = Realm.getDefaultInstance()

        if(bgRealm != null) {
            bgRealm.executeTransaction {

                val item = it.where<ChannelRealm>().equalTo("_id", id).findFirst()
                item?.deleteFromRealm()
            }

            bgRealm.close()
        }
        else{
            EventBus.getDefault().post(NullObjectAccessEvent("The realm is null!"))
        }

    }

    inner class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.chat_realm_name)

        var cardView : CardView = view.findViewById<CardView>(R.id.chat_card_view)
        var swipeLayout : SwipeLayout = view.findViewById<SwipeLayout>(R.id.chat_swipe_layout)
        var itemLayout : LinearLayout = view.findViewById<LinearLayout>(R.id.chat_item_layout)
        var bottomWrapper : LinearLayout = view.findViewById<LinearLayout>(R.id.chat_bottom_wrapper)
        var timeLayout : LinearLayout = view.findViewById<LinearLayout>(R.id.time_layout)

        var data: ChannelRealm? = null
        var isSelecting : Boolean = false

    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mItemManger.closeAllItems()

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(/*data*/channelsFilterList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(/*data*/channelsFilterList, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
        updateOrders()

        return true
    }

    private fun updateOrders(){
        val bgRealm = Realm.getDefaultInstance()
        for (i in 0 until channelsFilterList/*data*/.size) {
            launch {
                val tempChannel = bgRealm.where<ChannelRealm>().equalTo("_id", /*data*/channelsFilterList[i]._id)
                    .findFirst() as ChannelRealm
                if(tempChannel.order != i) {
                    bgRealm.executeTransaction { realm ->
                        tempChannel.order = i
                    }
                }
                else{
                    cancel()
                }
            }
        }
        bgRealm.close()
    }

    override fun getItemCount(): Int {
        return channelsFilterList/*data*/.size
    }

    override fun getFilter(): Filter {
        return ChannelsNamesFilter(this)
    }

    private class ChannelsNamesFilter(private val adapter: ChannelRealmAdapter) : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            return FilterResults()
        }

        override fun publishResults(
            constraint: CharSequence,
            results: FilterResults?
        ) {
            adapter.filterResults(constraint.toString())
        }

    }

    fun filterResults(text : String) {
        if (text.isEmpty()) {
            channelsFilterList = data
        } else {
            val resultList : MutableList<ChannelRealm> = ArrayList()
            for (row in data) {
                if (row.name.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                    resultList.add(row)
                }
            }
            channelsFilterList = resultList
        }
        setDataList(channelsFilterList)
    }
}