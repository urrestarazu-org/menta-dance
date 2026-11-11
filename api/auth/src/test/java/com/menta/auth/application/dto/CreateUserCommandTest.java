package com.menta.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.domain.model.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateUserCommandTest {

    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void requires_every_component() {
        assertThatThrownBy(() -> new CreateUserCommand(null, "a@b.com", "Ada", "Lovelace", Role.ADMIN))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreateUserCommand(ACTOR, null, "Ada", "Lovelace", Role.ADMIN))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreateUserCommand(ACTOR, "a@b.com", null, "Lovelace", Role.ADMIN))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreateUserCommand(ACTOR, "a@b.com", "Ada", null, Role.ADMIN))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreateUserCommand(ACTOR, "a@b.com", "Ada", "Lovelace", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void never_prints_the_personal_data() {
        CreateUserCommand command = new CreateUserCommand(ACTOR, "ada@example.com", "Ada", "Lovelace", Role.ADMIN);

        assertThat(command.toString())
            .contains("ADMIN")
            .doesNotContain("ada@example.com")
            .doesNotContain("Ada")
            .doesNotContain("Lovelace");
    }
}
