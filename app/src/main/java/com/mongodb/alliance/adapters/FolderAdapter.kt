package com.mongodb.alliance.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.mongodb.alliance.R
import com.mongodb.alliance.events.FolderPinDenyEvent
import com.mongodb.alliance.events.FolderPinEvent
import com.mongodb.alliance.events.FolderUnpinEvent
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import kotlin.coroutines.CoroutineContext


internal class FolderAdapter(var data: MutableList<FolderRealm>) : GlobalBroker.Publisher, GlobalBroker.Subscriber,
    ItemTouchHelperAdapter, RecyclerSwipeAdapter<FolderAdapter.FolderViewHolder>(), CoroutineScope {

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.new_folder_view_layout, parent, false)
        return FolderViewHolder(itemView)
    }

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return R.id.swipe_layout
    }

    internal inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var swipeLayout : SwipeLayout = view.findViewById<SwipeLayout>(R.id.swipe_layout)
        var itemLayout : LinearLayout = view.findViewById<LinearLayout>(R.id.item_layout)
        var bottomWrapper : LinearLayout = view.findViewById<LinearLayout>(R.id.bottom_wrapper)
        var name: TextView = view.findViewById(R.id.folder_name)
        var data: FolderRealm? = null
        var additional: TextView = view.findViewById(R.id.additional_count)

    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val obj: FolderRealm? = getItem(position)
        holder.data = obj
        holder.name.text = obj?.name

        holder.swipeLayout.showMode = SwipeLayout.ShowMode.LayDown

        holder.swipeLayout.addDrag(SwipeLayout.DragEdge.Left, holder.bottomWrapper)

        holder.swipeLayout.isRightSwipeEnabled = false

        if(holder.data != null) {
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

        holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_folder).setOnClickListener {
            mItemManger.closeAllItems()
            if(holder.data != null) {
                val isP = (holder.data as FolderRealm).isPinned
                if (!isP) {
                    val temp = findPinned()
                    if(temp != null){
                        EventBus.getDefault().post(FolderPinDenyEvent())
                    }
                    else {
                        holder.data?.let { it1 -> setPinned(it1, true) }
                        EventBus.getDefault().post(FolderPinEvent("", holder.data!!))
                        /*holder.itemLayout.findViewById<ImageView>(R.id.pinned).visibility =
                            View.VISIBLE
                        val pinButton =
                            holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_folder)
                        pinButton.setImageResource(R.drawable.ic_pin_blue_left)
                        pinButton.setBackgroundColor(Color.parseColor("#FFFFFF"))*/
                    }
                }
            }
        }
    }

    private fun getItem(position: Int) : FolderRealm?{
        return data[position]
    }

    fun setDataList(folders: MutableList<FolderRealm>) {
        data = folders
        notifyDataSetChanged()
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mItemManger.closeAllItems()

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(data, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(data, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
        updateOrders()

        return true
    }

    private fun updateOrders(){
        val bgRealm = Realm.getDefaultInstance()
        for (i in 0 until data.size) {
            launch {
                val tempFolder = bgRealm.where<FolderRealm>().equalTo("_id", data[i]._id)
                    .findFirst() as FolderRealm
                if(tempFolder.order != i) {
                    bgRealm.executeTransaction { realm ->
                        tempFolder.order = i
                    }
                }
                else{
                    cancel()
                }
            }
        }
        bgRealm.close()
    }

    private fun setPinned(folder : FolderRealm, pinned : Boolean) {
        val bgRealm = Realm.getDefaultInstance()

        runBlocking {
            val tempFolder = bgRealm.where<FolderRealm>().equalTo("_id", folder._id)
                .findFirst() as FolderRealm
            bgRealm.executeTransaction { realm ->
                tempFolder.isPinned = pinned
            }
        }

        bgRealm.close()
    }

    private fun findPinned() : FolderRealm?{
        val bgRealm = Realm.getDefaultInstance()

        val result = bgRealm.where<FolderRealm>().equalTo("isPinned", true)
                .findFirst()

        bgRealm.close()
        return result
    }

    override fun getItemCount(): Int {
        return data.count()
    }

    override fun getItemViewType(position: Int): Int {
        return if (data[position].isPinned) {
            0
        } else {
            1
        }
    }
}