package com.menta.android.di

import com.menta.android.data.remote.BearerTokenInterceptor
import com.menta.android.data.remote.TokenRefreshAuthenticator
import com.menta.android.data.session.AccessTokenSource
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

    /**
     * Derived from the auth client so it shares its connection pool, timeouts and the
     * no-replay rule: a connection failure during `/refresh` is never retried transparently.
     */
    @Provides @Singleton @ApiHttpClient
    fun apiHttpClient(@AuthHttpClient authClient: OkHttpClient, tokens: AccessTokenSource): OkHttpClient =
        authClient.newBuilder()
            .addInterceptor(BearerTokenInterceptor(tokens))
            .authenticator(TokenRefreshAuthenticator(tokens))
            .build()
}
