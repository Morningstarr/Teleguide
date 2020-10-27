package com.mongodb.alliance.events

import com.mongodb.alliance.adapters.FolderAdapter
import com.mongodb.alliance.model.FolderRealm

data class FolderPinDenyEvent(var message : String = "", var folder : FolderAdapter.FolderViewHolder?) {

}