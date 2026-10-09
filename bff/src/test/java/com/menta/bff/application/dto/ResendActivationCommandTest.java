package com.menta.bff.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ResendActivationCommand")
class ResendActivationCommandTest {

    @Test
    @DisplayName("keeps the email and the client address, which may be null")
    void keepsItsData() {
        ResendActivationCommand command = new ResendActivationCommand("user@example.com", null);

        assertThat(command.email()).isEqualTo("user@example.com");
        assertThat(command.clientAddress()).isNull();
    }

    @Test
    @DisplayName("rejects a null or blank email")
    void rejectsMissingEmail() {
        assertThatThrownBy(() -> new ResendActivationCommand(null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResendActivationCommand(" ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
