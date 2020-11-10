package com.mongodb.alliance.events

import com.mongodb.alliance.model.ChannelRealm

data class ChannelPinEvent(var message : String, var pinnedChannel : ChannelRealm) {

}