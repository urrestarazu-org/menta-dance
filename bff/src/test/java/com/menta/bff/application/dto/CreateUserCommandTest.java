package com.menta.bff.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateUserCommand and UserRole staff roles")
class CreateUserCommandTest {

    @Test
    @DisplayName("toString never shows the email or the names, only the role")
    void toStringHidesPersonalData() {
        String text = new CreateUserCommand("ada@example.com", "Ada", "Lovelace", UserRole.ADMIN).toString();

        assertThat(text).doesNotContain("ada@example.com").doesNotContain("Ada").doesNotContain("Lovelace")
                .contains("ADMIN");
    }

    @Test
    @DisplayName("the roles an administrator can create are the three staff roles, in screen order")
    void staffRoles() {
        assertThat(UserRole.staffRoles())
                .containsExactly(UserRole.INSTRUCTOR, UserRole.RECEPTIONIST, UserRole.ADMIN);
    }
}
