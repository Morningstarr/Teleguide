package com.mongodb.alliance.model


enum class ChannelType(val displayName: String) {
    groupChat("telegram_group_chat"),
    chat("telegram_chat"),
    channel("telegram_channel"),
}