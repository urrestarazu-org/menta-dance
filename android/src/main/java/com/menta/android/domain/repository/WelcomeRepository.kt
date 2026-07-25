package com.menta.android.domain.repository

import com.menta.android.domain.model.WelcomeMessage

interface WelcomeRepository {

    fun getWelcomeMessage(): WelcomeMessage
}
