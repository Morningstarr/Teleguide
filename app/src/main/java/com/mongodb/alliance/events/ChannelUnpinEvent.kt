package com.mongodb.alliance.events

import com.mongodb.alliance.model.ChannelRealm

data class ChannelUnpinEvent(var message : String = "", var channel : ChannelRealm?) {
}