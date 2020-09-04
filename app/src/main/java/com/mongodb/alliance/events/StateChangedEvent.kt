package com.mongodb.alliance.events

import com.mongodb.alliance.services.telegram.ClientState

data class StateChangedEvent (val clientState: ClientState) {

}
