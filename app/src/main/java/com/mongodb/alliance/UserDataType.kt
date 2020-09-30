package com.mongodb.alliance

enum class UserDataType(val displayName: String) {
    email("Email"),
    phoneNumber("Phone number"),
    telegramAccount("Telegram account"),
    password("Password")
}