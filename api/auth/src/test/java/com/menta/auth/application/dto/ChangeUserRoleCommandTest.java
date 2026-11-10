package com.menta.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.domain.model.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangeUserRoleCommandTest {

    @Test
    void rejects_a_missing_actor() {
        assertThatThrownBy(() -> new ChangeUserRoleCommand(null, UUID.randomUUID(), Role.STUDENT))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_a_missing_target_user() {
        assertThatThrownBy(() -> new ChangeUserRoleCommand(UUID.randomUUID(), null, Role.STUDENT))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_a_missing_role() {
        assertThatThrownBy(() -> new ChangeUserRoleCommand(UUID.randomUUID(), UUID.randomUUID(), null))
            .isInstanceOf(NullPointerException.class);
    }
}
