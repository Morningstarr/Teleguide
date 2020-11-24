package com.mongodb.alliance.events

import com.mongodb.alliance.adapters.ChannelRealmAdapter
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.time.ExperimentalTime

@InternalCoroutinesApi
data class ChannelPinDenyEvent @ExperimentalTime constructor(var message : String = "", var channel : ChannelRealmAdapter.ChannelViewHolder?) {

}