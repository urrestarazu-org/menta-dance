package com.menta.physical.domain.model;

import com.menta.physical.domain.exception.DeviceAlreadyRevokedException;
import com.menta.physical.domain.exception.DeviceRevokedException;
import java.time.Instant;
import java.util.Objects;

/**
 * A registered QR check-in device (#44, US-PHYSICAL-007).
 *
 * <p>Immutable-with-copy, like {@link PhysicalSession}: every transition returns a new instance
 * and throws before constructing an illegal one. {@code REVOKED} is an absorbing state (D5) —
 * {@link #revoke(Instant)} on an already-{@code REVOKED} device and {@link #rotateSecret(String,
 * Instant)} on a {@code REVOKED} device are both rejected, never a silent no-op.</p>
 *
 * <p>{@code secretHash} is the persisted lowercase SHA-256 hex digest of the device's raw
 * secret (D1) — the raw value itself never reaches this class or any persisted/listed type
 * (C2). This aggregate is inert in this change: nothing in the shipped check-in path
 * ({@code ProcessPhysicalCheckInUseCaseImpl}) reads or writes it (D2).</p>
 */
public final class PhysicalDevice {

    private final DeviceId id;
    private final String name;
    private final String location;
    private final String secretHash;
    private final DeviceStatus status;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    public PhysicalDevice(
        DeviceId id, String name, String location, String secretHash, DeviceStatus status, Instant expiresAt,
        Instant createdAt, Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        this.location = Objects.requireNonNull(location, "location cannot be null");
        if (location.isBlank()) {
            throw new IllegalArgumentException("location cannot be blank");
        }
        this.secretHash = Objects.requireNonNull(secretHash, "secretHash cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.expiresAt = expiresAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    /** Creates a brand-new device: fresh {@link DeviceId}, always {@code ACTIVE}. */
    public static PhysicalDevice register(
        String name, String location, String secretHash, Instant expiresAt, Instant now
    ) {
        return new PhysicalDevice(
            DeviceId.generate(), name, location, secretHash, DeviceStatus.ACTIVE, expiresAt, now, now
        );
    }

    /**
     * @throws DeviceRevokedException if this device is {@code REVOKED} (D5).
     */
    public PhysicalDevice rotateSecret(String newSecretHash, Instant now) {
        if (status == DeviceStatus.REVOKED) {
            throw new DeviceRevokedException();
        }
        return new PhysicalDevice(id, name, location, newSecretHash, status, expiresAt, createdAt, now);
    }

    /**
     * @throws DeviceAlreadyRevokedException if this device is already {@code REVOKED} (D5).
     */
    public PhysicalDevice revoke(Instant now) {
        if (status == DeviceStatus.REVOKED) {
            throw new DeviceAlreadyRevokedException();
        }
        return new PhysicalDevice(
            id, name, location, secretHash, DeviceStatus.REVOKED, expiresAt, createdAt, now
        );
    }

    public DeviceId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
