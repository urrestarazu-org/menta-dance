package com.menta.android.domain.usecase

import com.menta.android.domain.model.WelcomeMessage
import com.menta.android.domain.repository.WelcomeRepository
import javax.inject.Inject

class GetWelcomeMessageUseCase @Inject constructor(
    private val welcomeRepository: WelcomeRepository,
) {

    operator fun invoke(): WelcomeMessage = welcomeRepository.getWelcomeMessage()
}
