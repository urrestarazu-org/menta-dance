package com.menta.physical.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckInActorTest {

    @Test
    void anonymous_has_no_user_id_and_no_roles() {
        CheckInActor anonymous = CheckInActor.anonymous();

        assertThat(anonymous.userId()).isNull();
        assertThat(anonymous.roleNames()).isEmpty();
    }

    @Test
    void an_authenticated_actor_without_a_user_id_is_unrepresentable() {
        assertThatThrownBy(() -> new CheckInActor(null, Set.of("RECEPTIONIST")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void has_any_role_of_matches_a_present_role() {
        CheckInActor actor = new CheckInActor(UUID.randomUUID(), Set.of("RECEPTIONIST"));

        assertThat(actor.hasAnyRoleOf(Set.of("RECEPTIONIST", "ADMIN"))).isTrue();
    }

    @Test
    void has_any_role_of_returns_false_when_no_role_matches() {
        CheckInActor actor = new CheckInActor(UUID.randomUUID(), Set.of("STUDENT"));

        assertThat(actor.hasAnyRoleOf(Set.of("RECEPTIONIST", "ADMIN"))).isFalse();
    }

    @Test
    void anonymous_has_any_role_of_is_always_false() {
        assertThat(CheckInActor.anonymous().hasAnyRoleOf(Set.of("RECEPTIONIST", "ADMIN"))).isFalse();
    }

    @Test
    void role_names_is_defensively_copied() {
        Set<String> mutable = new java.util.HashSet<>(Set.of("RECEPTIONIST"));
        CheckInActor actor = new CheckInActor(UUID.randomUUID(), mutable);

        mutable.add("ADMIN");

        assertThat(actor.roleNames()).containsExactly("RECEPTIONIST");
    }

    @Test
    void null_role_names_normalizes_to_an_empty_set() {
        CheckInActor actor = new CheckInActor(null, null);

        assertThat(actor.roleNames()).isEmpty();
    }
}
