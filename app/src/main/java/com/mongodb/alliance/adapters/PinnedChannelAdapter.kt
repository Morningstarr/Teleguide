package com.mongodb.alliance.adapters

import android.graphics.Color
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
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.mongodb.alliance.R
import com.mongodb.alliance.events.*
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.FolderRealm
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.ChannelsRealmActivity
import com.mongodb.alliance.ui.FolderActivity
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.*
import org.bson.types.ObjectId
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.lang.Exception
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime

@InternalCoroutinesApi
@ExperimentalTime
internal class PinnedChannelAdapter @Inject constructor(var channel: ChannelRealm, var state : ClientState, var tService : Service) : GlobalBroker.Publisher,
    GlobalBroker.Subscriber, CoroutineScope,
    RecyclerSwipeAdapter<PinnedChannelAdapter.ChannelViewHolder>(){

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var folderId : String? = null

    var context : ChannelsRealmActivity? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.new_channels_realm_view, parent, false)
        return ChannelViewHolder(itemView)
    }

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return R.id.chat_swipe_layout
    }

    inner class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name: TextView = view.findViewById(R.id.chat_realm_name)
        var cardView : CardView = view.findViewById<CardView>(R.id.chat_card_view)
        var swipeLayout : SwipeLayout = view.findViewById<SwipeLayout>(R.id.chat_swipe_layout)
        var itemLayout : ConstraintLayout = view.findViewById<ConstraintLayout>(R.id.chat_item_layout)
        var bottomWrapper : LinearLayout = view.findViewById<LinearLayout>(R.id.chat_bottom_wrapper)
        var timeLayout : LinearLayout = view.findViewById<LinearLayout>(R.id.time_layout)

        var data: ChannelRealm? = null
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.data = channel
        holder.name.text = channel.displayName

        holder.swipeLayout.showMode = SwipeLayout.ShowMode.LayDown

        holder.swipeLayout.addDrag(SwipeLayout.DragEdge.Left, holder.bottomWrapper)

        holder.swipeLayout.isRightSwipeEnabled = false

        if(state == ClientState.waitParameters){
            runBlocking {
                (tService as TelegramService).setUpClient()
                state = (tService as TelegramService).returnClientState()
            }
        }
        loadChatData(holder, state, tService)

        if(holder.data != null) {
            if ((holder.data as ChannelRealm).isPinned) {
                mItemManger.bindView(holder.itemView, position)

                holder.itemLayout.findViewById<ImageView>(R.id.chat_pinned).visibility = View.VISIBLE
                val pinButton = holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_chat)
                pinButton.setImageResource(R.drawable.ic_pin_blue_left)
                pinButton.setBackgroundResource(R.drawable.pin_channel_shape_white)

            } else {
                mItemManger.bindView(holder.itemView, position)
            }
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

        holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_chat).setOnClickListener {
            mItemManger.closeAllItems()
            if(holder.data != null) {
                holder.data?.let { it1 -> setPinned(it1, false) }
                EventBus.getDefault().post(holder.data?.let { it1 ->
                    ChannelUnpinEvent("",
                        it1
                    )
                })

                val pinButton = holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_chat)
                pinButton.setImageResource(R.drawable.ic_pin)
                pinButton.setBackgroundResource(R.drawable.pin_channel_shape)
            }
        }

        holder.timeLayout.setOnClickListener {
            if(context?.isSelecting!!) {
                //EventBus.getDefault().post(SelectPinnedChatEvent())
                publish(SelectPinnedChatEvent())
            }
            else{
                holder.itemLayout.performClick()
            }
        }

        holder.timeLayout.setOnLongClickListener {
            publish(SelectPinnedChatEvent())
            //EventBus.getDefault().post()
            return@setOnLongClickListener true
        }


    }

    private fun loadChatData(holder : ChannelViewHolder, state: ClientState, tService: Service){

        if(state == ClientState.ready) {
            launch {
                val task = async {
                    holder.data?.name?.let {
                        (tService as TelegramService).getRecentMessage(it)
                        /*holder.itemLayout.findViewById<TextView>(R.id.chat_last_message).text =
                        . }*/
                    }
                }
                val messageData = task.await()
                if (messageData != null) {
                    holder.itemLayout.findViewById<TextView>(R.id.chat_last_message).text = messageData.keys.elementAt(0)
                    holder.itemLayout.findViewById<TextView>(R.id.message_time).text = messageData.values.elementAt(0)
                }

                holder.itemLayout.findViewById<TextView>(R.id.chat_last_message).visibility = View.VISIBLE
                if(messageData?.values?.elementAt(0) != "") {
                    holder.itemLayout.findViewById<TextView>(R.id.message_time).visibility =
                        View.VISIBLE
                }

                    Picasso.get().load(File(holder.data?.name?.let {
                        (tService as TelegramService).downloadImageFile(
                            it
                        )
                    })).into(holder.itemLayout.findViewById<ImageView>(R.id.chat_image),
                        object : Callback {
                            override fun onSuccess() {
                                holder.itemLayout.findViewById<TextView>(R.id.chat_image_placeholder).visibility =
                                    View.INVISIBLE
                                holder.itemLayout.findViewById<ImageView>(R.id.chat_image).visibility =
                                    View.VISIBLE
                            }

                            override fun onError(e: Exception?) {
                                holder.itemLayout.findViewById<TextView>(R.id.chat_image_placeholder).visibility =
                                    View.VISIBLE
                                holder.itemLayout.findViewById<ImageView>(R.id.chat_image).visibility =
                                    View.INVISIBLE
                            }
                        })

                val task2 = async {
                    holder.data?.name?.let { (tService as TelegramService).getUnreadCount(it) }
                }
                val count = task2.await()
                if (count!! > 0) {
                    holder.itemLayout.findViewById<TextView>(R.id.messages_count).text =
                        count.toString()
                } else {
                    holder.itemLayout.findViewById<TextView>(R.id.messages_count).visibility =
                        View.INVISIBLE
                }
            }
        }
    }

    fun addContext(activity : ChannelsRealmActivity){
        context = activity
    }

    fun findPinned() : ChannelRealm?{
        val bgRealm = Realm.getDefaultInstance()

        val result = bgRealm.where<ChannelRealm>().equalTo("folder._id", ObjectId(folderId)).equalTo("isPinned", true)
            .findFirst()

        bgRealm.close()
        return result
    }

    fun setFolderId(id : String){
        folderId = id
    }

    fun setPinned(channel : ChannelRealm, pinned : Boolean) {
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

    override fun getItemCount(): Int {
        return 1
    }

}