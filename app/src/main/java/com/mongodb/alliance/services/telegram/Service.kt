package com.mongodb.alliance.services.telegram

import androidx.appcompat.app.AppCompatActivity

interface Service {
    suspend fun initService(context : AppCompatActivity) : Any
    fun returnServiceObj() : Any
}