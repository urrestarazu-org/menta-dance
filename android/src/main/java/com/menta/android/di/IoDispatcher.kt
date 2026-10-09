package com.menta.android.di

import javax.inject.Qualifier

/** Dispatcher for the blocking repository calls; tests replace it with a test dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
