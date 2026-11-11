package com.menta.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivateAccountCommandTest {

    @Test
    void never_prints_the_token_or_the_password() {
        String printed = new ActivateAccountCommand("secret-token", "Chosen1Pass").toString();

        assertThat(printed).doesNotContain("secret-token").doesNotContain("Chosen1Pass");
    }
}
