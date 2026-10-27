package com.menta.android.di

import com.menta.android.BuildConfig
import com.menta.android.data.repository.OkHttpPhysicalSessionsRepository
import com.menta.android.domain.repository.PhysicalSessionsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PhysicalModule {
    @Provides @Singleton
    fun physicalSessionsRepository(@ApiHttpClient client: OkHttpClient): PhysicalSessionsRepository =
        OkHttpPhysicalSessionsRepository(client, BuildConfig.API_BASE_URL)
}
