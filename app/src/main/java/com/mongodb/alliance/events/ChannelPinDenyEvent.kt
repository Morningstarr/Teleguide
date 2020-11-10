package com.mongodb.alliance.events

import com.mongodb.alliance.adapters.ChannelRealmAdapter

data class ChannelPinDenyEvent(var message : String = "", var channel : ChannelRealmAdapter.ChannelViewHolder?) {

}