package com.mongodb.alliance.model


enum class ChannelType(val displayName: String) {
    groupChat("Telegram Group Chat"),
    chat("Telegram chat"),
    channel("Telegram channel"),
}