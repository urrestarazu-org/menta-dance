package com.menta.android.di

import javax.inject.Qualifier

/** Marks the OkHttp client for the auth endpoints: no Bearer, no automatic replays, no logging. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthHttpClient
