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
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.mongodb.alliance.R
import com.mongodb.alliance.events.*
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import kotlin.coroutines.CoroutineContext

internal class PinnedChannelAdapter(var channel: ChannelRealm) : GlobalBroker.Publisher,
    GlobalBroker.Subscriber, CoroutineScope,
    RecyclerSwipeAdapter<PinnedChannelAdapter.ChannelViewHolder>(){

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

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
        var itemLayout : LinearLayout = view.findViewById<LinearLayout>(R.id.chat_item_layout)
        var bottomWrapper : LinearLayout = view.findViewById<LinearLayout>(R.id.chat_bottom_wrapper)
        var timeLayout : LinearLayout = view.findViewById<LinearLayout>(R.id.time_layout)

        var data: ChannelRealm? = null
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.data = channel
        holder.name.text = channel.name

        holder.swipeLayout.showMode = SwipeLayout.ShowMode.LayDown

        holder.swipeLayout.addDrag(SwipeLayout.DragEdge.Left, holder.bottomWrapper)

        holder.swipeLayout.isRightSwipeEnabled = false

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
    }

    fun findPinned() : ChannelRealm?{
        val bgRealm = Realm.getDefaultInstance()

        val result = bgRealm.where<ChannelRealm>().equalTo("isPinned", true)
            .findFirst()

        bgRealm.close()
        return result
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