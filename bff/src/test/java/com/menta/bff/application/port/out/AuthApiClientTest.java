package com.menta.bff.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthApiClient exceptions")
class AuthApiClientTest {

    @Test
    @DisplayName("AuthenticationException carries message and cause")
    void authenticationException_carries_message_and_cause() {
        Throwable cause = new RuntimeException("root cause");

        AuthApiClient.AuthenticationException exception =
                new AuthApiClient.AuthenticationException("invalid credentials", cause);

        assertThat(exception.getMessage()).isEqualTo("invalid credentials");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("RefreshTokenRevokedException carries message and cause")
    void refreshTokenRevokedException_carries_message_and_cause() {
        Throwable cause = new RuntimeException("root cause");

        AuthApiClient.RefreshTokenRevokedException exception =
                new AuthApiClient.RefreshTokenRevokedException("token family revoked", cause);

        assertThat(exception.getMessage()).isEqualTo("token family revoked");
        assertThat(exception.getCause()).isEqualTo(cause);
    }
}
