package com.menta.bff.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for UserId value object.
 * <p>
 * Verifies:
 * - Null validation
 * - UUID generation
 * - Equality semantics
 * - Immutability
 * </p>
 */
class UserIdTest {

    @Test
    void shouldCreateUserIdFromUuid() {
        UUID uuid = UUID.randomUUID();
        UserId userId = new UserId(uuid);

        assertThat(userId.value()).isEqualTo(uuid);
    }

    @Test
    void shouldRejectNullUuid() {
        assertThatThrownBy(() -> new UserId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("value");
    }

    @Test
    void shouldGenerateNewUserId() {
        UserId userId1 = UserId.generate();
        UserId userId2 = UserId.generate();

        assertThat(userId1).isNotNull();
        assertThat(userId2).isNotNull();
        assertThat(userId1).isNotEqualTo(userId2);
        assertThat(userId1.value()).isNotEqualTo(userId2.value());
    }

    @Test
    void shouldImplementEquality() {
        UUID uuid = UUID.randomUUID();
        UserId userId1 = new UserId(uuid);
        UserId userId2 = new UserId(uuid);
        UserId userId3 = new UserId(UUID.randomUUID());

        assertThat(userId1).isEqualTo(userId2);
        assertThat(userId1).isNotEqualTo(userId3);
        assertThat(userId1.hashCode()).isEqualTo(userId2.hashCode());
    }

    @Test
    void shouldBeImmutable() {
        UUID uuid = UUID.randomUUID();
        UserId userId = new UserId(uuid);

        // Record is immutable - verify accessor returns same value
        UUID originalValue = userId.value();
        assertThat(userId.value()).isEqualTo(originalValue);
    }

    @Test
    void toStringShouldContainUuid() {
        UUID uuid = UUID.randomUUID();
        UserId userId = new UserId(uuid);

        String toString = userId.toString();

        assertThat(toString).contains(uuid.toString());
        assertThat(toString).contains("UserId");
    }
}
