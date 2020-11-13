package com.mongodb.alliance.events

import com.mongodb.alliance.adapters.ChannelRealmAdapter
import org.bson.types.ObjectId

data class SelectFolderToMoveEvent(var folderId : ObjectId?) {
}