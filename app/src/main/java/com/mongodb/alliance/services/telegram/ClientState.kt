package com.mongodb.alliance.services.telegram

enum class ClientState(val displayName: String) {
    waitNumber("Wait Number"),
    waitCode("Wait Code"),
    waitPassword("Wait Password"),
    ready("State Ready"),
}