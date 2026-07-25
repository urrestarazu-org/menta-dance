package com.menta.android.di

import com.menta.android.data.repository.StaticWelcomeRepository
import com.menta.android.domain.repository.WelcomeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AndroidBindingsModule {

    @Binds
    abstract fun bindWelcomeRepository(
        implementation: StaticWelcomeRepository,
    ): WelcomeRepository
}
