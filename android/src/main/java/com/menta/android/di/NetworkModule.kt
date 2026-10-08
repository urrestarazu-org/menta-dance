package com.menta.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val TIMEOUT_SECONDS = 10L

    /**
     * `retryOnConnectionFailure(false)`: a transparent replay of a `/refresh` that the
     * server already processed would reuse the rotated token and revoke every session.
     * No logging interceptor is installed, so tokens never reach a log (ADR-0025).
     */
    @Provides @Singleton @AuthHttpClient
    fun authHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
}
