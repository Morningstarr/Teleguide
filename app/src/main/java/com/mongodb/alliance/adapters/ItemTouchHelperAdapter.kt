package com.mongodb.alliance.adapters

interface ItemTouchHelperAdapter {
    fun onItemMove(fromPosition: Int, toPosition: Int) : Boolean

    //fun onItemDismiss(position: Int)
}