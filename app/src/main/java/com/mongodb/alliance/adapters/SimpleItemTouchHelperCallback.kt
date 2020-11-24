package com.mongodb.alliance.adapters

import android.app.Application
import android.graphics.Canvas
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.TintTypedArray.obtainStyledAttributes
import androidx.cardview.widget.CardView
import androidx.core.content.res.use
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mongodb.alliance.ChannelProj
import com.mongodb.alliance.R
import com.mongodb.alliance.channelApp
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.time.ExperimentalTime

@ExperimentalTime
@InternalCoroutinesApi
class SimpleItemTouchHelperCallback(var adapter: ItemTouchHelperAdapter) : ItemTouchHelper.Callback() {


    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                             dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        val view = viewHolder.itemView
        view.translationX = dX
        view.translationY = dY
        if (isCurrentlyActive) {
            if(view.findViewById<CardView>(R.id.chat_card_view) != null) {
                view.findViewById<CardView>(R.id.chat_card_view).cardElevation = 10f
            }
            if(view.findViewById<CardView>(R.id.card_view) != null) {
                view.findViewById<CardView>(R.id.card_view).cardElevation = 10f
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        val view = viewHolder.itemView
        view.translationX = 0f
        view.translationY = 0f
        if(view.findViewById<CardView>(R.id.chat_card_view) != null) {
            if(view.findViewById<ImageView>(R.id.check_chat).visibility != View.VISIBLE) {
                view.findViewById<CardView>(R.id.chat_card_view).cardElevation = 0f
            }
        }
        if(view.findViewById<CardView>(R.id.card_view) != null) {
            (adapter as FolderAdapter).context?.binding?.fldrFab?.show()
            if(view.findViewById<ImageView>(R.id.check_folder).visibility != View.VISIBLE) {
                view.findViewById<CardView>(R.id.card_view).cardElevation = 0f
            }
        }
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        var dragFlags = 0
        if((recyclerView.adapter is FolderAdapter && !(recyclerView.adapter as FolderAdapter).isPaste) ||
            (recyclerView.adapter is PinnedFolderAdapter && !(recyclerView.adapter as PinnedFolderAdapter).returnPasteMode())) {
            if(recyclerView.adapter is FolderAdapter) {
                (adapter as FolderAdapter).context?.binding?.fldrFab?.hide()
            }
            dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        }
        return makeMovementFlags(
            dragFlags,
            0
        )
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        if(viewHolder is FolderAdapter.FolderViewHolder) {
            if (!viewHolder.isSelecting) {
                adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                //(adapter as FolderAdapter).context?.binding?.fldrFab?.show()
                return true
            } else {
                viewHolder.cardView.cardElevation = 10f
                return false
            }
        }
        else{
            if(viewHolder is ChannelRealmAdapter.ChannelViewHolder){
                if(!viewHolder.isSelecting) {
                    adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                    return true
                }
                else{
                    viewHolder.cardView.cardElevation = 10f
                    return false
                }
            }
            else {
                return false
            }
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

    }


}