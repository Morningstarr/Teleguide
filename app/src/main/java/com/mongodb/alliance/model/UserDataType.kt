package com.mongodb.alliance.model

enum class UserDataType(val displayName: String) {
    email("Email"),
    phoneNumber("Phone number"),
    telegramAccount("Telegram account"),
    password("Password")
}