package com.mongodb.alliance.model

import com.mongodb.alliance.services.telegram.ClientState

data class StateChangedEvent (val clientState: ClientState) {

}
