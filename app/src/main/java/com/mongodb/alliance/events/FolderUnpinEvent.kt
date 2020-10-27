package com.mongodb.alliance.events

import com.mongodb.alliance.model.FolderRealm

data class FolderUnpinEvent(var message : String = "", var folder : FolderRealm?) {
}