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
import com.mongodb.alliance.model.FolderRealm
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.*
import org.bson.types.ObjectId
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.lang.Exception
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime
class ChannelRealmAdapter  @Inject constructor(var data: MutableList<ChannelRealm>, var state : ClientState, var tService : Service) :
    GlobalBroker.Publisher, RecyclerSwipeAdapter<ChannelRealmAdapter.ChannelViewHolder>(),
        ItemTouchHelperAdapter, Filterable, CoroutineScope
{
    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var selectedChannels : MutableList<ChannelRealm> = ArrayList()

    private var channelsFilterList : MutableList<ChannelRealm> = ArrayList()

    init {
        channelsFilterList = data
    }

    private var folderId : String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val itemView: View = LayoutInflater.from(parent.context).inflate(R.layout.channel_realm_view, parent, false)
        return ChannelViewHolder(itemView)
    }

    private fun getItem(position: Int) : ChannelRealm? {
        return channelsFilterList[position]
    }

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return R.id.chat_swipe_layout
    }

    @ExperimentalCoroutinesApi
    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        if(channelsFilterList[position].isValid) {
            val obj: ChannelRealm? = getItem(position)
            holder.data = obj
            holder.name.text = obj?.displayName

            holder.swipeLayout.showMode = SwipeLayout.ShowMode.LayDown

            holder.swipeLayout.addDrag(SwipeLayout.DragEdge.Left, holder.bottomWrapper)

            holder.swipeLayout.isRightSwipeEnabled = false

            holder.itemLayout.findViewById<TextView>(R.id.chat_image_placeholder).text = holder.data?.displayName?.get(0)
                .toString()

            if(state == ClientState.waitParameters){
                runBlocking {
                    (tService as TelegramService).setUpClient()
                    state = (tService as TelegramService).returnClientState()
                }
            }
            //loadChatData(holder, state, tService)
            getChatMessageData(holder)

            if (holder.data != null) {
                mItemManger.bindView(holder.itemView, position)
            }

            holder.swipeLayout.addSwipeListener(object : SwipeLayout.SwipeListener {
                override fun onClose(layout: SwipeLayout?) {

                }

                override fun onUpdate(layout: SwipeLayout?, leftOffset: Int, topOffset: Int) {

                }

                override fun onStartOpen(layout: SwipeLayout?) {
                    mItemManger.closeAllExcept(layout)
                }

                override fun onOpen(layout: SwipeLayout?) {

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

            holder.itemLayout.setOnClickListener {
                if(selectedChannels.size <= 0){
                    holder.data?.name?.let { it1 -> openChannel(it1) }
                }
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
                            holder.data?.let { it1 -> setPinned(it1) }
                            EventBus.getDefault().post(ChannelPinEvent("", holder.data!!))
                        }
                    }
                }
            }

            holder.timeLayout.setOnLongClickListener {
                if (!holder.isSelecting) {
                    holder.isSelecting = true
                    EventBus.getDefault().post(SelectChatEvent(true))
                    holder.cardView.cardElevation = 10f
                    showCheck(true, holder)
                    holder.data?.let { it1 -> selectedChannels.add(it1) }
                    return@setOnLongClickListener true
                } else {
                    return@setOnLongClickListener false
                }
            }

            holder.timeLayout.setOnClickListener {
                if (selectedChannels.size > 0) {
                    if (holder.isSelecting) {
                        holder.isSelecting = false
                        EventBus.getDefault().post(SelectChatEvent(false))
                        showCheck(false, holder)
                        holder.cardView.cardElevation = 0f
                        selectedChannels.remove(holder.data)
                    } else {
                        holder.isSelecting = true
                        EventBus.getDefault().post(SelectChatEvent(true))
                        showCheck(true, holder)
                        holder.cardView.cardElevation = 10f
                        holder.data?.let { it1 -> selectedChannels.add(it1) }
                    }
                }
            }

            if (holder.isSelecting && !selectedChannels.contains(holder.data)) {
                holder.isSelecting = false
            }

            val checkButton = holder.timeLayout.findViewById<ImageView>(R.id.check_chat)
            if (!holder.isSelecting && checkButton.visibility == View.VISIBLE) {
                showCheck(false, holder)
                holder.cardView.cardElevation = 0f
            }
            if (holder.isSelecting && checkButton.visibility == View.INVISIBLE) {
                showCheck(true, holder)
                holder.cardView.cardElevation = 10f
            }
            if (selectedChannels.contains(holder.data) && checkButton.visibility == View.INVISIBLE) {
                showCheck(true, holder)
                holder.cardView.cardElevation = 10f
            }
        }
    }

    private fun findPinned() : ChannelRealm?{
        val bgRealm = Realm.getDefaultInstance()

        val result = bgRealm.where<ChannelRealm>().equalTo("folder._id", ObjectId(folderId)).equalTo("isPinned", true)
            .findFirst()

        bgRealm.close()
        return result
    }

    private fun getChatMessageData(holder : ChannelViewHolder){
        val lastMessageText = holder.itemLayout.findViewById<TextView>(R.id.chat_last_message)
        val lastMessageTimeText = holder.itemLayout.findViewById<TextView>(R.id.chat_last_message_time)
        val results = holder.data?.displayName?.let { (tService as TelegramService).returnRecentMessage(it) }
        val unreadCountText = holder.itemLayout.findViewById<TextView>(R.id.chat_unread_count)
        val chatImage = holder.itemLayout.findViewById<ImageView>(R.id.chat_image)
        val imagePlaceholderText = holder.itemLayout.findViewById<TextView>(R.id.chat_image_placeholder)
        lastMessageText.text = results?.keys?.elementAt(0)
        lastMessageTimeText.text = results?.values?.elementAt(0)
        lastMessageText.visibility = View.VISIBLE
        if(results?.values?.elementAt(0) != "") {
            lastMessageTimeText.visibility =
                View.VISIBLE
        }
        val count = holder.data?.displayName?.let { (tService as TelegramService).returnUnreadCount(it) }
        if(count != null) {
            if (count > 0) {
                unreadCountText.visibility =
                    View.VISIBLE
                unreadCountText.text = count.toString()
            }
            else{
                unreadCountText.visibility =
                    View.INVISIBLE
            }
        }
        launch {
            Picasso.get().load(File(holder.data?.displayName?.let {
                (tService as TelegramService).returnImagePath(
                    it
                )
            })).into(chatImage,
                object : Callback {
                    override fun onSuccess() {
                        imagePlaceholderText.visibility =
                            View.INVISIBLE
                        chatImage.visibility =
                            View.VISIBLE
                    }

                    override fun onError(e: Exception?) {
                        imagePlaceholderText.visibility =
                            View.VISIBLE
                        chatImage.visibility =
                            View.INVISIBLE
                    }
                })
        }
    }

    @ExperimentalCoroutinesApi
    private fun loadChatData(holder : ChannelViewHolder, state: ClientState, tService : Service) {
        if(state == ClientState.ready) {
            val lastMessageText = holder.itemLayout.findViewById<TextView>(R.id.chat_last_message)
            val lastMessageTimeText = holder.itemLayout.findViewById<TextView>(R.id.chat_last_message_time)
            val unreadCountText = holder.itemLayout.findViewById<TextView>(R.id.chat_unread_count)
            val chatImage = holder.itemLayout.findViewById<ImageView>(R.id.chat_image)
            val imagePlaceholderText = holder.itemLayout.findViewById<TextView>(R.id.chat_image_placeholder)
            launch {
                val task = async {
                    holder.data?.name?.let {
                        (tService as TelegramService).getRecentMessage(it)
                    }
                }
                val messageData = task.await()
                if (messageData != null) {
                    lastMessageText.text = messageData.keys.elementAt(0)
                    lastMessageTimeText.text = messageData.values.elementAt(0)
                }

                lastMessageText.visibility = View.VISIBLE
                if(messageData?.values?.elementAt(0) != "") {
                    lastMessageTimeText.visibility =
                        View.VISIBLE
                }

                Picasso.get().load(File(holder.data?.name?.let {
                    (tService as TelegramService).downloadImageFile(
                        it
                    )
                })).into(chatImage,
                    object : Callback {
                        override fun onSuccess() {
                            imagePlaceholderText.visibility =
                                View.INVISIBLE
                            chatImage.visibility =
                                View.VISIBLE
                        }

                        override fun onError(e: Exception?) {
                            imagePlaceholderText.visibility =
                                View.VISIBLE
                            chatImage.visibility =
                                View.INVISIBLE
                        }
                    })


                val task2 = async {
                    holder.data?.name?.let { (tService as TelegramService).getUnreadCount(it) }
                }
                val count = task2.await()
                if(count != null) {
                    if (count > 0) {
                        unreadCountText.visibility =
                            View.VISIBLE
                        unreadCountText.text = count.toString()
                    }
                    else{
                        unreadCountText.visibility =
                            View.INVISIBLE
                    }
                }
            }
        }
    }

    fun setFolderId(id : String){
        folderId = id
    }

    private fun setPinned(channel : ChannelRealm) {
        val bgRealm = Realm.getDefaultInstance()

        runBlocking {
            bgRealm.executeTransaction { realm ->
                val tempChannel = realm.where<ChannelRealm>().equalTo("_id", channel._id)
                    .findFirst() as ChannelRealm

                tempChannel.isPinned = true
            }
        }

        bgRealm.close()
    }

    fun setDataList(newData : MutableList<ChannelRealm>){
        channelsFilterList = newData
        notifyDataSetChanged()
    }

    fun cancelSelection(){
        for(i in 0..channelsFilterList.size){
            notifyItemChanged(i)
        }
        selectedChannels.clear()
    }

    fun deleteSelected(){
        val bgRealm = Realm.getDefaultInstance()

        for (channel in selectedChannels) {
            bgRealm.executeTransaction { realm ->
                val results = realm.where<ChannelRealm>().equalTo("_id", channel._id).findFirst()
                val folder = results?.folder
                val foundFolder = realm.where<FolderRealm>().equalTo("_id", folder?._id).findFirst()
                if (foundFolder != null) {
                    foundFolder.nestedCount = foundFolder.nestedCount - 1
                }
                results?.deleteFromRealm()
            }
        }

        selectedChannels.clear()
        bgRealm.close()
    }

    fun moveChannels(newFolder : FolderRealm){
        val bgRealm = Realm.getDefaultInstance()

        for (channel in selectedChannels) {
            bgRealm.executeTransaction { realm ->
                val results = realm.where<ChannelRealm>().equalTo("_id", channel._id).findFirst()
                results?.folder = newFolder
                val maxOrderValue =
                    realm.where<ChannelRealm>().equalTo("folder._id", newFolder._id).findAll().max("order")
                if(maxOrderValue != null) {
                    results?.order = maxOrderValue.toInt() + 1
                }
                else{
                    results?.order = 1
                }
            }
        }

        selectedChannels.clear()
        bgRealm.close()
    }

    private fun openChannel(username: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("http://www.t.me/$username")
        publish(OpenChannelEvent(intent))
    }

    inner class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.chat_realm_name)

        var cardView : CardView = view.findViewById(R.id.chat_card_view)
        var swipeLayout : SwipeLayout = view.findViewById(R.id.chat_swipe_layout)
        var itemLayout : ConstraintLayout = view.findViewById(R.id.chat_item_layout)
        var bottomWrapper : LinearLayout = view.findViewById(R.id.chat_bottom_wrapper)
        var timeLayout : LinearLayout = view.findViewById(R.id.chat_time_layout)

        var data: ChannelRealm? = null
        var isSelecting : Boolean = false

    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mItemManger.closeAllItems()

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(channelsFilterList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(channelsFilterList, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
        updateOrders()

        return true
    }

    private fun updateOrders(){
        val bgRealm = Realm.getDefaultInstance()
        for (i in 0 until channelsFilterList.size) {
            launch {
                val tempChannel = bgRealm.where<ChannelRealm>().equalTo("_id", channelsFilterList[i]._id)
                    .findFirst() as ChannelRealm
                if(tempChannel.order != i) {
                    bgRealm.executeTransaction {
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
        return channelsFilterList.size
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
                if (row.displayName.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                    resultList.add(row)
                }
            }
            channelsFilterList = resultList
        }
        setDataList(channelsFilterList)
    }

    private fun showCheck(show : Boolean, holder : ChannelViewHolder){
        if(show) {
            holder.timeLayout.findViewById<TextView>(R.id.chat_last_message_time).visibility =
                View.INVISIBLE
            holder.timeLayout.findViewById<TextView>(R.id.chat_unread_count).visibility =
                View.INVISIBLE
            holder.timeLayout.findViewById<ImageView>(R.id.check_chat).visibility =
                View.VISIBLE
        }
        else {
            holder.timeLayout.findViewById<TextView>(R.id.chat_last_message_time).visibility =
                View.VISIBLE
            val count = runBlocking {
                holder.data?.name?.let { (tService as TelegramService).getUnreadCount(it) }
            }
            if (count != null) {
                if (count > 0){
                    holder.timeLayout.findViewById<TextView>(R.id.chat_unread_count).visibility =
                        View.VISIBLE
                }
                else{
                    holder.timeLayout.findViewById<TextView>(R.id.chat_unread_count).visibility =
                        View.INVISIBLE
                }
            }
            holder.timeLayout.findViewById<ImageView>(R.id.check_chat).visibility =
                View.INVISIBLE
        }
    }

}