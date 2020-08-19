package com.mongodb.alliance.services.telegram

import androidx.appcompat.app.AppCompatActivity

interface Service {
    suspend fun initService()
    fun returnServiceObj() : Any
}