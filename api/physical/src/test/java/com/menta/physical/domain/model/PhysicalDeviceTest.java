package com.menta.physical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.physical.domain.exception.DeviceAlreadyRevokedException;
import com.menta.physical.domain.exception.DeviceRevokedException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhysicalDeviceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final String HASH_1 = "a".repeat(64);
    private static final String HASH_2 = "b".repeat(64);

    private static PhysicalDevice device(DeviceStatus status) {
        return new PhysicalDevice(
            DeviceId.generate(), "Puerta principal", "Salon 1", HASH_1, status, null, NOW, NOW
        );
    }

    @Test
    void register_creates_an_active_device_with_all_fields_set() {
        Instant expiresAt = NOW.plusSeconds(3600);

        PhysicalDevice registered = PhysicalDevice.register("Puerta principal", "Salon 1", HASH_1, expiresAt, NOW);

        assertThat(registered.getName()).isEqualTo("Puerta principal");
        assertThat(registered.getLocation()).isEqualTo("Salon 1");
        assertThat(registered.getSecretHash()).isEqualTo(HASH_1);
        assertThat(registered.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(registered.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(registered.getCreatedAt()).isEqualTo(NOW);
        assertThat(registered.getUpdatedAt()).isEqualTo(NOW);
        assertThat(registered.getId()).isNotNull();
    }

    @Test
    void register_accepts_a_null_expires_at() {
        PhysicalDevice registered = PhysicalDevice.register("Puerta principal", "Salon 1", HASH_1, null, NOW);

        assertThat(registered.getExpiresAt()).isNull();
    }

    @Test
    void rotate_secret_on_active_returns_a_new_instance_with_the_new_hash_receiver_unchanged() {
        PhysicalDevice active = device(DeviceStatus.ACTIVE);
        Instant rotatedAt = NOW.plusSeconds(60);

        PhysicalDevice rotated = active.rotateSecret(HASH_2, rotatedAt);

        assertThat(rotated.getSecretHash()).isEqualTo(HASH_2);
        assertThat(rotated.getUpdatedAt()).isEqualTo(rotatedAt);
        assertThat(rotated.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(rotated.getId()).isEqualTo(active.getId());
        assertThat(active.getSecretHash()).isEqualTo(HASH_1);
    }

    @Test
    void rotate_secret_on_revoked_throws_device_revoked_exception() {
        PhysicalDevice revoked = device(DeviceStatus.REVOKED);

        assertThatThrownBy(() -> revoked.rotateSecret(HASH_2, NOW.plusSeconds(60)))
            .isInstanceOf(DeviceRevokedException.class);
    }

    @Test
    void revoke_on_active_transitions_to_revoked() {
        PhysicalDevice active = device(DeviceStatus.ACTIVE);
        Instant revokedAt = NOW.plusSeconds(60);

        PhysicalDevice revoked = active.revoke(revokedAt);

        assertThat(revoked.getStatus()).isEqualTo(DeviceStatus.REVOKED);
        assertThat(revoked.getUpdatedAt()).isEqualTo(revokedAt);
        assertThat(revoked.getId()).isEqualTo(active.getId());
        assertThat(active.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
    }

    @Test
    void revoke_on_already_revoked_throws_device_already_revoked_exception() {
        PhysicalDevice revoked = device(DeviceStatus.REVOKED);

        assertThatThrownBy(() -> revoked.revoke(NOW.plusSeconds(60)))
            .isInstanceOf(DeviceAlreadyRevokedException.class);
    }

    @Test
    void rejects_blank_name_or_location() {
        assertThatThrownBy(() -> new PhysicalDevice(
            DeviceId.generate(), " ", "Salon 1", HASH_1, DeviceStatus.ACTIVE, null, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> new PhysicalDevice(
            DeviceId.generate(), "Puerta", " ", HASH_1, DeviceStatus.ACTIVE, null, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("location");
    }

    @Test
    void rejects_null_required_fields() {
        assertThatThrownBy(() -> new PhysicalDevice(
            null, "Puerta", "Salon 1", HASH_1, DeviceStatus.ACTIVE, null, NOW, NOW
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalDevice(
            DeviceId.generate(), "Puerta", "Salon 1", null, DeviceStatus.ACTIVE, null, NOW, NOW
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalDevice(
            DeviceId.generate(), "Puerta", "Salon 1", HASH_1, null, null, NOW, NOW
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalDevice(
            DeviceId.generate(), "Puerta", "Salon 1", HASH_1, DeviceStatus.ACTIVE, null, null, NOW
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PhysicalDevice(
            DeviceId.generate(), "Puerta", "Salon 1", HASH_1, DeviceStatus.ACTIVE, null, NOW, null
        )).isInstanceOf(NullPointerException.class);
    }
}
