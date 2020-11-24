package com.mongodb.alliance.di

import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ApplicationComponent
import kotlinx.coroutines.InternalCoroutinesApi
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.ExperimentalTime

@Qualifier
annotation class TelegramServ

@InternalCoroutinesApi
@ExperimentalTime

@InstallIn(ApplicationComponent::class)
@Module
abstract class ServiceModule {
    @TelegramServ
    @Singleton
    @Binds
    abstract fun bindService(impl: TelegramService): Service
}