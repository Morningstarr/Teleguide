package com.mongodb.alliance.adapters

import android.graphics.Color
import android.util.EventLog
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
import com.mongodb.alliance.databinding.NewFolderViewLayoutBinding
import com.mongodb.alliance.events.EditFolderEvent
import com.mongodb.alliance.events.FolderPinDenyEvent
import com.mongodb.alliance.events.FolderPinEvent
import com.mongodb.alliance.events.SelectFolderEvent
import com.mongodb.alliance.model.FolderRealm
import com.mongodb.alliance.ui.AddFolderFragment
import com.mongodb.alliance.ui.EditFolderFragment
import io.realm.Realm
import io.realm.kotlin.delete
import io.realm.kotlin.where
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import kotlin.coroutines.CoroutineContext


class FolderAdapter(var data: MutableList<FolderRealm>) : GlobalBroker.Publisher, GlobalBroker.Subscriber,
    ItemTouchHelperAdapter, RecyclerSwipeAdapter<FolderAdapter.FolderViewHolder>(), CoroutineScope,
    Filterable {

    var selectedFolders : MutableList<FolderRealm> = ArrayList()

    var foldersFilterList : MutableList<FolderRealm> = ArrayList()

    init {
        foldersFilterList = data
    }

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

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var swipeLayout : SwipeLayout = view.findViewById<SwipeLayout>(R.id.swipe_layout)
        var itemLayout : LinearLayout = view.findViewById<LinearLayout>(R.id.item_layout)
        var bottomWrapper : LinearLayout = view.findViewById<LinearLayout>(R.id.bottom_wrapper)
        var checkLayout : ConstraintLayout = view.findViewById<ConstraintLayout>(R.id.check_layout)
        var name: TextView = view.findViewById(R.id.folder_name)
        var data: FolderRealm? = null
        var additional: TextView = view.findViewById(R.id.additional_count)
        var isSelecting : Boolean = false
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        if (/*data*/foldersFilterList.get(position).isValid()) {
            val obj: FolderRealm? = getItem(position)
            holder.data = obj
            holder.name.text = obj?.name

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

            holder.checkLayout.setOnLongClickListener {
                if (!holder.isSelecting) {
                    EventBus.getDefault().post(SelectFolderEvent(true))
                    holder.checkLayout.findViewById<ImageView>(R.id.check_folder).visibility =
                        View.VISIBLE
                    holder.isSelecting = true
                    holder.data?.let { it1 -> selectedFolders.add(it1) }
                    return@setOnLongClickListener true
                } else {
                    return@setOnLongClickListener false
                }
            }

            holder.checkLayout.setOnClickListener {
                if (!holder.isSelecting) {
                    EventBus.getDefault().post(SelectFolderEvent(true))
                    holder.checkLayout.findViewById<ImageView>(R.id.check_folder).visibility =
                        View.VISIBLE
                    holder.isSelecting = true
                    holder.data?.let { it1 -> selectedFolders.add(it1) }

                } else {
                    EventBus.getDefault().post(SelectFolderEvent(false))
                    holder.data?.let { it1 -> selectedFolders.remove(it1) }
                    holder.checkLayout.findViewById<ImageView>(R.id.check_folder).visibility =
                        View.GONE
                    holder.isSelecting = false
                }
            }

            holder.itemLayout.setOnClickListener {
                if (holder.isSelecting) {
                    /*if (!selectedFolders.contains(holder.data)) {
                        EventBus.getDefault().post(SelectFolderEvent(true))
                        holder.data?.let { it1 -> selectedFolders.add(it1) }
                        holder.checkLayout.findViewById<ImageView>(R.id.check_folder).visibility =
                            View.VISIBLE
                    } else {
                        EventBus.getDefault().post(SelectFolderEvent(false))
                        holder.data?.let { it1 -> selectedFolders.remove(it1) }
                        holder.isSelecting = false
                        holder.itemLayout.findViewById<ImageView>(R.id.check_folder).visibility =
                            View.GONE
                    }*/
                } else {
                    //todo open folder
                }
            }

            holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_folder).setOnClickListener {
                mItemManger.closeAllItems()
                if (holder.data != null) {
                    val isP = (holder.data as FolderRealm).isPinned
                    if (!isP) {
                        val temp = findPinned()
                        if (temp != null) {
                            EventBus.getDefault().post(FolderPinDenyEvent("", holder))
                        } else {
                            holder.data?.let { it1 -> setPinned(it1, true) }
                            EventBus.getDefault().post(FolderPinEvent("", holder.data!!))
                        }
                    }
                }
            }

            holder.bottomWrapper.findViewById<ImageButton>(R.id.edit_folder).setOnClickListener {
                mItemManger.closeAllItems()
                EventBus.getDefault().post(holder.data?.let { it1 -> EditFolderEvent(it1) })
            }

            if(holder.isSelecting && !selectedFolders.contains(holder.data)){
                holder.isSelecting = false
            }

            val checkButton = holder.checkLayout.findViewById<ImageView>(R.id.check_folder)
            if (!holder.isSelecting && checkButton.visibility == View.VISIBLE) {
                checkButton.visibility = View.GONE
            }
        }
    }

    private fun getItem(position: Int) : FolderRealm?{
        return /*data*/foldersFilterList[position]
    }

    fun setDataList(folders: MutableList<FolderRealm>) {
        /*data*/ foldersFilterList = folders
        //cancelSelection()
        notifyDataSetChanged()
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mItemManger.closeAllItems()

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(/*data*/foldersFilterList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(/*data*/foldersFilterList, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
        updateOrders()

        return true
    }

    private fun updateOrders(){
        val bgRealm = Realm.getDefaultInstance()
        for (i in 0 until foldersFilterList/*data*/.size) {
            launch {
                val tempFolder = bgRealm.where<FolderRealm>().equalTo("_id", /*data*/foldersFilterList[i]._id)
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
        return foldersFilterList.count()
    }

    override fun getItemViewType(position: Int): Int {
        return 0
    }

    open fun cancelSelection(){
        for(i in 0../*data*/foldersFilterList.size){
            notifyItemChanged(i)
        }
        selectedFolders.clear()
    }

    open fun deleteSelected(){
        val bgRealm = Realm.getDefaultInstance()

        for (folder in selectedFolders) {
            bgRealm.executeTransaction { realm ->
                val results = realm.where<FolderRealm>().equalTo("_id", folder._id).findFirst()

                results?.deleteFromRealm()

            }


        }

        selectedFolders.clear()

        bgRealm.close()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charSearch = constraint.toString()
                if (charSearch.isEmpty()) {
                    foldersFilterList = data
                } else {
                    val resultList : MutableList<FolderRealm> = ArrayList()
                    for (row in data) {
                        if (row.name.toLowerCase(Locale.ROOT).contains(charSearch.toLowerCase(Locale.ROOT))) {
                            resultList.add(row)
                        }
                    }
                    foldersFilterList = resultList
                }
                val filterResults = FilterResults()
                filterResults.values = foldersFilterList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if(results?.values != null) {
                    foldersFilterList = results.values as MutableList<FolderRealm>
                    notifyDataSetChanged()
                }
            }
        }
    }

}