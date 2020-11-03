package com.mongodb.alliance.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.mongodb.alliance.R
import com.mongodb.alliance.events.*
import com.mongodb.alliance.model.FolderRealm
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import kotlin.coroutines.CoroutineContext

internal class PinnedFolderAdapter(var folder: FolderRealm) : GlobalBroker.Publisher,
    GlobalBroker.Subscriber, CoroutineScope,
    RecyclerSwipeAdapter<PinnedFolderAdapter.FolderViewHolder>(){

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
        var checkLayout : ConstraintLayout = view.findViewById<ConstraintLayout>(R.id.check_layout)
        var isSelecting : Boolean = false
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.data = folder
        holder.name.text = folder.name

        holder.swipeLayout.showMode = SwipeLayout.ShowMode.LayDown

        holder.swipeLayout.addDrag(SwipeLayout.DragEdge.Left, holder.bottomWrapper)

        holder.swipeLayout.isRightSwipeEnabled = false

        if(holder.data != null) {
            if ((holder.data as FolderRealm).isPinned) {
                mItemManger.bindView(holder.itemView, position)
                holder.itemLayout.findViewById<ImageView>(R.id.pinned).visibility = View.VISIBLE
                val pinButton = holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_folder)
                pinButton.setImageResource(R.drawable.ic_pin_blue_left)
                pinButton.setBackgroundColor(Color.parseColor("#FFFFFF"))
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

        holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_folder).setOnClickListener {
            mItemManger.closeAllItems()
            if(holder.data != null) {
                holder.data?.let { it1 -> setPinned(it1, false) }
                EventBus.getDefault().post(holder.data?.let { it1 ->
                    FolderUnpinEvent("",
                        it1
                    )
                })
                holder.itemLayout.findViewById<ImageView>(R.id.pinned).visibility = View.INVISIBLE

                val pinButton = holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_folder)
                pinButton.setImageResource(R.drawable.ic_pin)
                pinButton.setBackgroundColor(Color.parseColor("#03CCFC"))
            }
        }

        holder.bottomWrapper.findViewById<ImageButton>(R.id.edit_folder).setOnClickListener{
            mItemManger.closeAllItems()
            EventBus.getDefault().post(holder.data?.let { it1 -> EditFolderEvent(it1) })
        }
    }

    fun findPinned() : FolderRealm?{
        val bgRealm = Realm.getDefaultInstance()

        val result = bgRealm.where<FolderRealm>().equalTo("isPinned", true)
            .findFirst()

        bgRealm.close()
        return result
    }

    fun setPinned(folder : FolderRealm, pinned : Boolean) {
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

    override fun getItemCount(): Int {
        return 1
    }

}