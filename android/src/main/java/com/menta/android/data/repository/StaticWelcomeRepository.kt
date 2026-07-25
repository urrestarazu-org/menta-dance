package com.menta.android.data.repository

import com.menta.android.domain.model.WelcomeMessage
import com.menta.android.domain.repository.WelcomeRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaticWelcomeRepository @Inject constructor() : WelcomeRepository {

    override fun getWelcomeMessage(): WelcomeMessage =
        WelcomeMessage("Menta Dance Android is ready.")
}
