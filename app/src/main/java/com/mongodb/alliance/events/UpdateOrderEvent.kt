package com.mongodb.alliance.events

import com.mongodb.alliance.model.FolderRealm

class UpdateOrderEvent (var message: String, var data : MutableList<FolderRealm>) {

}