package com.menta.android.di

import android.content.Context
import com.menta.android.BuildConfig
import com.menta.android.data.local.EncryptedRefreshTokenStore
import com.menta.android.data.remote.AuthRemoteDataSource
import com.menta.android.data.remote.HttpAuthRemoteDataSource
import com.menta.android.data.repository.AuthSessionRepository
import com.menta.android.domain.repository.RefreshTokenStore
import com.menta.android.domain.repository.SessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AndroidAuthModule {
    @Provides @Singleton
    fun refreshTokenStore(@ApplicationContext context: Context): RefreshTokenStore = EncryptedRefreshTokenStore(context)

    @Provides @Singleton
    fun authRemoteDataSource(): AuthRemoteDataSource = HttpAuthRemoteDataSource(BuildConfig.AUTH_API_BASE_URL)

    @Provides @Singleton
    fun sessionRepository(store: RefreshTokenStore, remote: AuthRemoteDataSource): SessionRepository =
        AuthSessionRepository(store, remote)
}
