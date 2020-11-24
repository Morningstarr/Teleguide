package com.mongodb.alliance.events

import com.mongodb.alliance.model.FolderRealm

data class FolderPinEvent(var message : String, var pinnedFolder : FolderRealm) {
    
}