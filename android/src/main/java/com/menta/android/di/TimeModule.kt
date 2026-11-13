package com.menta.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.menta.android.domain.time.DeviceZone
import java.time.Clock
import java.time.ZoneId

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    fun provideDeviceZone(): DeviceZone = DeviceZone { ZoneId.systemDefault() }
}
