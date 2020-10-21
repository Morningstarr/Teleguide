package com.mongodb.alliance.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.mongodb.alliance.R
import com.mongodb.alliance.events.NullObjectAccessEvent
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import java.util.*
import kotlin.coroutines.CoroutineContext


internal class FolderAdapter(var data: MutableList<FolderRealm>) : GlobalBroker.Publisher,
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

        mItemManger.bindView(holder.itemView, position)
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
    }

    fun getItem(position: Int) : FolderRealm?{
        return data[position]
    }


    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        //val prev: FolderRealm = data.removeAt(fromPosition)
        //data.add(if (toPosition > fromPosition) toPosition - 1 else toPosition, prev)
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(data, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(data, i, i - 1)
            }
        }
        try {
            launch {
                withContext(Dispatchers.IO) {
                    updateOrder(data[fromPosition], toPosition)
                    updateOrder(data[toPosition], fromPosition)
                }
            }
        }
        catch(e:Exception){
            EventBus.getDefault().post(e.message?.let { NullObjectAccessEvent(it) })
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun getItemCount(): Int {
        return data.count()
    }

    private fun updateOrder(updated : FolderRealm, position: Int){
        val realm = Realm.getDefaultInstance()
        realm.executeTransactionAsync {
            updated.order = position
        }
    }
}