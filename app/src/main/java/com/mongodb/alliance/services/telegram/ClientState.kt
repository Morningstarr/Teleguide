package com.mongodb.alliance.services.telegram

enum class ClientState(val displayName: String) {
    waitNumber("Wait Number"),
    waitCode("Wait Code"),
    waitPassword("Wait Password"),
    waitParameters("Wait Parameters"),
    ready("State Ready"),
    setParameters("Set Parameters")
}