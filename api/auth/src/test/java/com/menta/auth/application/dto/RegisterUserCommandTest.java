package com.menta.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.domain.model.Role;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RegisterUserCommandTest {

    @Test
    void to_string_masks_the_credentials_and_the_personal_data() {
        RegisterUserCommand command = new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT,
            "Ada", "Lovelace", "+5491100000000", LocalDate.of(1990, 5, 17), "client-fp"
        );

        assertThat(command.toString())
            .doesNotContain("student@example.com")
            .doesNotContain("Sup3rSecret!")
            .doesNotContain("Ada")
            .doesNotContain("Lovelace")
            .doesNotContain("+5491100000000")
            .doesNotContain("1990")
            .doesNotContain("client-fp")
            .contains("role=STUDENT");
    }
}
