package com.menta.android

import com.menta.android.domain.model.WelcomeMessage
import com.menta.android.domain.repository.WelcomeRepository
import com.menta.android.domain.usecase.GetWelcomeMessageUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidSmokeTest {

    @Test
    fun getWelcomeMessageUseCase_returnsRepositoryMessage() {
        val useCase = GetWelcomeMessageUseCase(
            object : WelcomeRepository {
                override fun getWelcomeMessage(): WelcomeMessage =
                    WelcomeMessage("Smoke test passed")
            },
        )

        assertEquals("Smoke test passed", useCase().text)
    }
}
