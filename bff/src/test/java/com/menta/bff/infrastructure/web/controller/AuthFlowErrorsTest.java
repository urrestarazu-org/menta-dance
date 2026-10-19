package com.menta.bff.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.port.out.AuthApiClient;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;

@DisplayName("AuthFlowErrors - rate limit wait time")
class AuthFlowErrorsTest {

    static Stream<Arguments> waits() {
        return Stream.of(
                Arguments.of(null, "Esperá unos minutos"),
                Arguments.of(Duration.ZERO, "Esperá unos minutos"),
                Arguments.of(Duration.ofSeconds(30), "Esperá 30 segundos"),
                Arguments.of(Duration.ofSeconds(60), "Esperá 1 minuto "),
                Arguments.of(Duration.ofSeconds(61), "Esperá 2 minutos"),
                Arguments.of(Duration.ofMinutes(15), "Esperá 15 minutos"));
    }

    @ParameterizedTest
    @MethodSource("waits")
    @DisplayName("a 429 tells the user how long to wait, when the API said so")
    void rateLimitMessageCarriesTheWait(Duration retryAfter, String expected) {
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthFlowErrors.apply(new AuthApiClient.RateLimitedException("limit", retryAfter), model, response);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat((String) model.getAttribute("formError")).contains("demasiados intentos").contains(expected);
    }
}
