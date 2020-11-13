package com.mongodb.alliance.events

import com.mongodb.alliance.model.FolderRealm

data class MoveConfirmEvent(var folder : FolderRealm) {
}