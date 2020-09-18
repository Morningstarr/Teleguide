package com.mongodb.alliance.events

import com.mongodb.alliance.services.telegram.ClientState

data class TelegramConnectedEvent (val clientState: ClientState) {

}