package com.menta.android.di

import javax.inject.Qualifier

/** Marks the OkHttp client for authenticated API calls: Bearer injection plus a single refresh-and-retry. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiHttpClient
