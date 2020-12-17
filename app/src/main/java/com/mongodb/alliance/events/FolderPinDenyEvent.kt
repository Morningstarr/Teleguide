package com.mongodb.alliance.events

import com.mongodb.alliance.adapters.FolderAdapter
import com.mongodb.alliance.model.FolderRealm
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.time.ExperimentalTime

@InternalCoroutinesApi
data class FolderPinDenyEvent @ExperimentalTime constructor(var message : String = "", var folder : FolderAdapter.FolderViewHolder?,
                                                                var folderObj : FolderRealm?) {

}